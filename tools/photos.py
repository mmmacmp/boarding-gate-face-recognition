"""Shared helpers for local volunteer photos (data/volunteers/, gitignored). Used by seed.py and gate_sim.py.

Layout per volunteer (see data/README-consent.md); .jpg, .jpeg and .png are all accepted:
  reference.*          enrolment photo, served by mock-idp
  probe.*, probe_02.*  gate captures (easy, hard, ...), sorted by name
  reference_twin.*     optional: enrolled as "<id>-TWIN" on the separate twin demo flight (a simulation)
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


def discover_volunteers(data_dir: Path) -> list[Volunteer]:
    volunteers = []
    for folder in sorted(p for p in (data_dir / "volunteers").glob("*") if p.is_dir()):
        v = Volunteer(profile_id=folder.name)
        for img in _images(folder):
            if img.stem == "reference":
                v.reference = img
            elif img.stem == "reference_twin":
                v.twin_reference = img
            elif img.stem == "probe" or img.stem.startswith("probe_"):
                v.probes.append(img)
        volunteers.append(v)
    return volunteers


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
