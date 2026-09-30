from pathlib import Path

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.settings import DEFAULT_MODELS_DIR, Settings

DATA_DIR = Path(__file__).resolve().parents[2] / "data"  # gitignored; consenting volunteers only


def jpeg(img: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return buf.tobytes()


def blank_image() -> bytes:
    return jpeg(np.full((480, 640, 3), 128, np.uint8))  # grey frame: no face


def volunteer_references() -> list[Path]:
    return sorted(DATA_DIR.glob("volunteers/*/reference.jpg"))


@pytest.fixture(scope="module")
def r50_client():
    with TestClient(create_app(Settings(device="cpu", model="r50"))) as c:  # `with` runs the lifespan (model load)
        yield c


@pytest.fixture(scope="module")
def sface_client():
    if not (DEFAULT_MODELS_DIR / "face_recognition_sface_2021dec.onnx").exists():
        pytest.skip("SFace model not downloaded (python download_models.py)")
    with TestClient(create_app(Settings(device="cpu", model="sface"))) as c:
        yield c
