"""Build the DEMO01 assets in data/demo/ (gitignored) from the local DigiFace-1M subset (research licence).

- Picks 3 synthetic identities that behave cleanly (high genuine score, low scores against everyone else in the
  demo gallery, incl. P001) and copies them as S01, S02, S03 (reference.jpg + probe.jpg).
- Generates the special probes from OTHER synthetic identities and checks each one with the running model-service:
    special/no_face.jpg    heavily blurred face   -> 0 faces            -> NO_FACE
    special/two_faces.jpg  two faces side by side -> 2 faces            -> MULTI_FACE
    special/tiny_face.jpg  a face ~45 px wide     -> only small faces   -> LOW_QUALITY
Needs the model-service on :8001. Usage: python tools/make_demo_assets.py
"""
import shutil
from pathlib import Path

import cv2
import httpx
import numpy as np

from photos import clean_jpeg, discover_volunteers, simulated_lookalike

ROOT = Path(__file__).resolve().parents[1]
DATA, OUT = ROOT / "data", ROOT / "data" / "demo"
T = 0.44  # the R50 threshold in application.yml


def faces(ms: httpx.Client, data: bytes) -> dict:
    return ms.post("/v1/embed", content=data, headers={"Content-Type": "image/jpeg"}).json()


def embedding(ms: httpx.Client, data: bytes) -> np.ndarray:
    body = faces(ms, data)
    assert len(body["faces"]) == 1, "expected exactly one face"
    return np.array(body["faces"][0]["embedding"])


def jpeg(img: np.ndarray) -> bytes:
    return cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 92])[1].tobytes()


def read(path: Path) -> np.ndarray:
    return cv2.imdecode(np.fromfile(str(path), np.uint8), cv2.IMREAD_COLOR)


def main():
    p001 = next(v for v in discover_volunteers(DATA) if v.profile_id == "P001")
    synthetic = [v for v in discover_volunteers(DATA, "synthetic")
                 if v.profile_id.startswith("S") and v.profile_id != "S035" and v.reference and v.probes]
    with httpx.Client(base_url="http://localhost:8001", timeout=30) as ms:
        people = {"P001": embedding(ms, clean_jpeg(p001.reference)),
                  "P001-TWIN": embedding(ms, simulated_lookalike(p001.reference))}
        scored = []
        for v in synthetic:
            ref, probe = embedding(ms, clean_jpeg(v.reference)), embedding(ms, clean_jpeg(v.probes[0]))
            scored.append((float(ref @ probe), v, ref, probe))
        scored.sort(key=lambda s: -s[0])

        chosen = []  # greedy: best genuine score, and every cross score < 0.30 so margins stay large
        for genuine, v, ref, probe in scored:
            others = list(people.values()) + [c[1] for c in chosen]  # references already in the demo gallery
            cross = max(float(probe @ o) for o in others)
            if genuine >= T + 0.15 and cross < 0.30 and all(float(c[2] @ ref) < 0.30 for c in chosen):
                chosen.append((v, ref, probe, genuine, cross))
            if len(chosen) == 3:
                break
        assert len(chosen) == 3, "could not find 3 clean synthetic identities"

        if OUT.exists():
            shutil.rmtree(OUT)
        for i, (v, _, _, genuine, cross) in enumerate(chosen, start=1):
            target = OUT / f"S0{i}"
            target.mkdir(parents=True)
            shutil.copy(v.reference, target / "reference.jpg")
            shutil.copy(v.probes[0], target / "probe.jpg")
            print(f"S0{i} <- DigiFace {v.profile_id}: genuine {genuine:.3f}, highest other score {cross:.3f}")

        spare = [v for _, v, _, _ in scored if v not in [c[0] for c in chosen]]
        special = OUT / "special"
        special.mkdir()
        no_face = cv2.GaussianBlur(read(spare[0].probes[0]), (0, 0), 30)
        two = np.hstack([read(spare[1].reference), read(spare[2].reference)])
        tiny = np.full((480, 640, 3), 128, np.uint8)
        small = cv2.resize(read(spare[3].probes[0]), None, fx=0.25, fy=0.25, interpolation=cv2.INTER_AREA)
        tiny[180:180 + small.shape[0], 260:260 + small.shape[1]] = small
        for name, img, check in (("no_face", no_face, lambda b: not b["faces"] and not b["small_faces"]),
                                 ("two_faces", two, lambda b: len(b["faces"]) == 2),
                                 ("tiny_face", tiny, lambda b: not b["faces"] and b["small_faces"] >= 1)):
            data = jpeg(img)
            body = faces(ms, data)
            assert check(body), f"{name}: unexpected detection {len(body['faces'])} faces, {body['small_faces']} small"
            (special / f"{name}.jpg").write_bytes(data)
            print(f"special/{name}.jpg: {len(body['faces'])} faces in zone, {body['small_faces']} too small")

    (OUT / "SOURCE.txt").write_text(
        "DEMO01 assets. S01-S03 and special/ are derived from DigiFace-1M (Microsoft, WACV 2023), Research Use of\n"
        "Data Agreement v1.0: non-commercial research only; keep attribution. P001 is the consenting volunteer.\n"
        + "".join(f"S0{i}: DigiFace {c[0].profile_id}\n" for i, c in enumerate(chosen, start=1)), encoding="utf-8")


if __name__ == "__main__":
    main()
