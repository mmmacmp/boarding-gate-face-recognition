"""API behaviour with a fake backend: fast, no model files, no photos."""
import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.models import Face
from app.settings import Settings
from conftest import blank_image


class FakeBackend:
    model_id, dim, device, providers = "fake", 4, "cpu", ["fake"]

    def __init__(self, n_faces: int = 0, widths: list[int] | None = None):
        self.widths = widths if widths is not None else [200] * n_faces  # face widths in pixels
        self.embed_calls = 0

    def detect(self, img):
        return [Face(bbox=[0, 0, w, w], det_score=0.9, landmarks=np.zeros(1)) for w in self.widths]

    def embed(self, img, face):
        self.embed_calls += 1
        return np.array([1.0, 0.0, 0.0, 0.0], np.float32)

    def warmup(self):
        pass


def client_with(n_faces: int, **settings) -> tuple[TestClient, FakeBackend]:
    backend = FakeBackend(n_faces)
    return TestClient(create_app(Settings(**settings), backend=backend)), backend


@pytest.mark.parametrize("widths, min_face_px, expected_faces, expect_embedding", [
    ([200, 40], 80, 1, True),     # passenger + small background face -> one face, embedded
    ([40, 60], 80, 0, False),     # only background faces -> no face
    ([200, 150], 80, 2, False),   # two faces in the zone -> still MULTI_FACE territory
    ([200, 40], 0, 2, False),     # MIN_FACE_PX=0 disables the filter
    ([80], 80, 1, True),          # boundary: exactly MIN_FACE_PX is kept
])
def test_min_face_px_ignores_small_faces(widths, min_face_px, expected_faces, expect_embedding):
    backend = FakeBackend(widths=widths)
    client = TestClient(create_app(Settings(min_face_px=min_face_px), backend=backend))
    faces = client.post("/v1/embed", content=blank_image()).json()["faces"]
    assert len(faces) == expected_faces
    assert all(f["bbox"][2] - f["bbox"][0] >= min_face_px for f in faces)  # small faces are not even reported
    assert (backend.embed_calls == 1) == expect_embedding


@pytest.mark.parametrize("n_faces, expect_embedding", [(0, False), (1, True), (2, False), (3, False)])
def test_embedding_only_for_exactly_one_face(n_faces, expect_embedding):
    client, backend = client_with(n_faces)
    r = client.post("/v1/embed", content=blank_image(), headers={"Content-Type": "image/jpeg"})
    assert r.status_code == 200
    body = r.json()
    assert body["model_id"] == "fake" and body["dim"] == 4
    assert len(body["faces"]) == n_faces
    assert all(("embedding" in f) == expect_embedding for f in body["faces"])
    assert backend.embed_calls == (1 if expect_embedding else 0)  # bystander templates are never computed
    assert {"detect", "embed", "total"} <= body["timings_ms"].keys()


def test_empty_body_is_400():
    client, _ = client_with(1)
    assert client.post("/v1/embed", content=b"").status_code == 400


def test_non_image_is_400():
    client, _ = client_with(1)
    assert client.post("/v1/embed", content=b"definitely not a jpeg").status_code == 400


def test_oversized_image_is_413():
    client, _ = client_with(1, max_image_bytes=100)
    assert client.post("/v1/embed", content=blank_image()).status_code == 413


def test_health_reports_model_and_device():
    client, _ = client_with(1)
    with client:
        body = client.get("/health").json()
    assert body == {"status": "ok", "model_id": "fake", "dim": 4, "device": "cpu", "providers": ["fake"]}


def test_metrics_endpoint_exposed():
    client, _ = client_with(1)
    client.post("/v1/embed", content=blank_image())
    assert "http_requests_total" in client.get("/metrics").text


def test_small_faces_are_counted_for_capture_feedback():
    backend = FakeBackend(widths=[40, 60])
    client = TestClient(create_app(Settings(min_face_px=80), backend=backend))
    body = client.post("/v1/embed", content=blank_image()).json()
    assert body["faces"] == [] and body["small_faces"] == 2


def test_down_fault_returns_503_until_cleared():
    client, _ = client_with(1)
    assert client.post("/admin/faults", json={"down": True}).json()["down"] is True
    assert client.post("/v1/embed", content=blank_image()).status_code == 503
    client.post("/admin/faults", json={})
    assert client.post("/v1/embed", content=blank_image()).status_code == 200
