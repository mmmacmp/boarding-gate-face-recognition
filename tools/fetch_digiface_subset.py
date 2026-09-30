"""Fetch a small subset of DigiFace-1M into data/synthetic/ WITHOUT downloading the whole 2.94 GB zip.

DigiFace-1M (Microsoft, WACV 2023, https://github.com/microsoft/DigiFace1M): rendered 3D faces, no real people.
Licence: Research Use of Data Agreement v1.0, NON-COMMERCIAL RESEARCH ONLY. Images stay local (data/ is gitignored)
and are never shown in the demo recording.

How: the server supports HTTP Range requests, so a seekable file object over HTTP lets Python's zipfile read only
the zip's central directory (the index) and the few members we pick.
Usage: python tools/fetch_digiface_subset.py [--identities 50] [--images 3]
"""
import argparse
import io
import zipfile
from pathlib import Path

import httpx

URL = "https://facesyntheticspubwedata.z6.web.core.windows.net/wacv-2023/subjects_0-1999_72_imgs.zip"
ROOT = Path(__file__).resolve().parents[1]


class HttpRangeFile(io.RawIOBase):
    """Read-only, seekable view of a remote file; every read becomes one 'Range: bytes=a-b' request."""

    def __init__(self, client: httpx.Client, url: str):
        self.client, self.url, self.pos = client, url, 0
        self.size = int(client.head(url).headers["content-length"])
        self.requests = 0
        self.bytes_read = 0

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, offset, whence=io.SEEK_SET):
        base = {io.SEEK_SET: 0, io.SEEK_CUR: self.pos, io.SEEK_END: self.size}[whence]
        self.pos = base + offset
        return self.pos

    def readinto(self, buffer):
        if self.pos >= self.size:
            return 0
        end = min(self.pos + len(buffer), self.size) - 1
        r = self.client.get(self.url, headers={"Range": f"bytes={self.pos}-{end}"})
        if r.status_code != 206:
            raise IOError(f"server ignored the Range header (HTTP {r.status_code})")
        data = r.content
        buffer[:len(data)] = data
        self.pos += len(data)
        self.requests += 1
        self.bytes_read += len(data)
        return len(data)


def prepare(raw_dir: Path, out_dir: Path) -> None:
    """Make the 112x112 RGBA crops look like camera photos to the detector, in the volunteer layout.

    Alpha is flattened onto grey, the crop is upscaled x2.5 and centred on a 480x480 grey canvas: without context
    SCRFD misses tightly cropped faces, and the upscale lifts face width above the 150 px reference guideline.
    Layout: S001/reference.jpg, probe.jpg, probe_02.jpg ...; the LAST identity becomes STRANGER/ with probes only,
    so it can never be enrolled (the NO_MATCH "stranger" in the demo).
    """
    import cv2
    import numpy as np

    identities = sorted((p for p in raw_dir.iterdir() if p.is_dir()), key=lambda p: int(p.name))
    for i, ident in enumerate(identities):
        stranger = i == len(identities) - 1
        target = out_dir / ("STRANGER" if stranger else f"S{i + 1:03d}")
        target.mkdir(parents=True, exist_ok=True)
        images = sorted(ident.glob("*.png"), key=lambda p: int(p.stem))
        names = (["probe", "probe_02", "probe_03"] if stranger else ["reference", "probe", "probe_02"])
        for name, path in zip(names, images):
            im = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
            alpha = im[:, :, 3:4].astype(np.float32) / 255
            rgb = (im[:, :, :3].astype(np.float32) * alpha + 128 * (1 - alpha)).astype(np.uint8)
            rgb = cv2.resize(rgb, None, fx=2.5, fy=2.5, interpolation=cv2.INTER_CUBIC)
            pad = (480 - rgb.shape[0]) // 2
            rgb = cv2.copyMakeBorder(rgb, pad, 480 - rgb.shape[0] - pad, pad, 480 - rgb.shape[1] - pad,
                                     cv2.BORDER_CONSTANT, value=(128, 128, 128))
            cv2.imwrite(str(target / f"{name}.jpg"), rgb, [cv2.IMWRITE_JPEG_QUALITY, 95])
    (out_dir / "SOURCE.txt").write_text(
        "DigiFace-1M subset (Microsoft, WACV 2023), https://github.com/microsoft/DigiFace1M\n"
        f"Source zip: {URL} (first {len(identities)} identities, first 3 images each, via HTTP range requests)\n"
        "Licence: Research Use of Data Agreement v1.0 - non-commercial research only. Synthetic (rendered) faces.\n"
        "Preprocessing: alpha flattened on grey, x2.5 upscale, centred on a 480x480 grey canvas.\n"
        "Local only (gitignored). Never shown in the demo recording.\n", encoding="utf-8")
    print(f"prepared {len(identities) - 1} synthetic passengers + STRANGER in {out_dir}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--identities", type=int, default=50, help="including one 'stranger' kept off the manifest")
    ap.add_argument("--images", type=int, default=3, help="per identity: reference + probes")
    ap.add_argument("--out", type=Path, default=ROOT / "data" / "synthetic" / "raw")
    ap.add_argument("--list-only", action="store_true")
    ap.add_argument("--prepare-only", action="store_true", help="skip the download, re-run preparation on raw/")
    args = ap.parse_args()
    if args.prepare_only:
        prepare(args.out, args.out.parent)
        return

    with httpx.Client(timeout=60, follow_redirects=True) as client:
        remote = HttpRangeFile(client, URL)
        with zipfile.ZipFile(io.BufferedReader(remote, buffer_size=1 << 20)) as zf:
            names = [n for n in zf.namelist() if not n.endswith("/")]
            print(f"zip: {remote.size / 1e9:.2f} GB, {len(names)} files; index read with {remote.requests} "
                  f"requests, {remote.bytes_read / 1e6:.1f} MB")
            by_identity: dict[str, list[str]] = {}
            for n in names:
                by_identity.setdefault(n.rsplit("/", 1)[0], []).append(n)
            identities = sorted(by_identity)[: args.identities]
            print(f"first entries: {names[:3]}; picking {len(identities)} identities x {args.images} images")
            if args.list_only:
                return
            for ident in identities:
                target = args.out / Path(ident).name
                target.mkdir(parents=True, exist_ok=True)
                for member in sorted(by_identity[ident])[: args.images]:
                    (target / Path(member).name).write_bytes(zf.read(member))
            print(f"done: {remote.requests} requests, {remote.bytes_read / 1e6:.1f} MB transferred -> {args.out}")
    prepare(args.out, args.out.parent)


if __name__ == "__main__":
    main()
