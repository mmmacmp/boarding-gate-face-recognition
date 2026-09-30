"""Shared helpers for local volunteer photos (data/volunteers/, gitignored). Used by seed.py and gate_sim.py.

Layout per volunteer (see data/README-consent.md); .jpg, .jpeg and .png are all accepted:
  reference.*          enrolment photo, served by mock-idp
  probe.*, probe_02.*  gate captures (easy, hard, ...), sorted by name
  reference_twin.*     optional second photo; only used with seed.py --twin-from-photo (reproduces a finding)
data/synthetic/ uses the same layout (S001 ... S049, plus STRANGER with probes only).
"""
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

IMAGE_SUFFIXES = (".jpg", ".jpeg", ".png")
REFERENCE_MIN_FACE_PX = 150  # guideline for enrolment photos; the gate itself ignores faces < MIN_FACE_PX (80)


@dataclass
class Volunteer:
    profile_id: str
    reference: Path | None = None
    twin_reference: Path | None = None
    probes: list[Path] = field(default_factory=list)


def _images(folder: Path) -> list[Path]:
    return sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)


def discover_volunteers(data_dir: Path, subdir: str = "volunteers") -> list[Volunteer]:
    """Profiles under data/<subdir>/ ("volunteers" = consenting people, "synthetic" = DigiFace-1M subset)."""
    volunteers = []
    for folder in sorted(p for p in (data_dir / subdir).glob("*") if p.is_dir()):
        v = Volunteer(profile_id=folder.name)
        for img in _images(folder):
            if img.stem == "reference":
                v.reference = img
            elif img.stem == "reference_twin":
                v.twin_reference = img
            elif img.stem == "probe" or img.stem.startswith("probe_"):
                v.probes.append(img)
        if v.reference or v.probes:  # skips helper folders such as synthetic/raw
            volunteers.append(v)
    return volunteers


def find_profile(data_dir: Path, profile_id: str) -> Volunteer | None:
    for subdir in ("volunteers", "synthetic"):
        for v in discover_volunteers(data_dir, subdir):
            if v.profile_id == profile_id:
                return v
    return None


def simulated_lookalike(reference: Path) -> bytes:
    """SIMULATED look-alike for the AMBIGUOUS demo: a re-cropped, re-compressed copy of a real reference.

    Same person, same capture domain, so a probe scores almost equally against both profiles and the margin rule
    deterministically returns AMBIGUOUS. It is labelled as a simulation everywhere; it is not a real look-alike.
    """
    img = cv2.imdecode(np.fromfile(str(reference), np.uint8), cv2.IMREAD_COLOR)
    h, w = img.shape[:2]
    dy, dx = h // 25, w // 25                      # trim 4% from each side
    ok, buf = cv2.imencode(".jpg", img[dy:h - dy, dx:w - dx], [cv2.IMWRITE_JPEG_QUALITY, 70])
    if not ok:
        raise ValueError(f"could not re-encode: {reference}")
    return buf.tobytes()


def clean_jpeg(path: Path) -> bytes:
    """Decode and re-encode as a plain JPEG: drops EXIF/XMP metadata (camera, GPS, timestamps).

    OpenCV applies the EXIF orientation while decoding, so the pixels stay upright after the metadata is gone.
    np.fromfile + imdecode (not imread) so non-ASCII Windows paths work.
    """
    img = cv2.imdecode(np.fromfile(str(path), np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError(f"not a decodable image: {path}")
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 95])
    if not ok:
        raise ValueError(f"could not re-encode: {path}")
    return buf.tobytes()


def has_exif(data: bytes) -> bool:
    return b"Exif\x00\x00" in data[:65536]  # the APP1 EXIF header sits near the start of a JPEG
