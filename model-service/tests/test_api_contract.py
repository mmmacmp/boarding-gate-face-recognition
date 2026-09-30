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

    def __init__(self, n_faces: int):
        self.n_faces = n_faces
        self.embed_calls = 0

    def detect(self, img):
        return [Face(bbox=[0, 0, 10, 10], det_score=0.9, landmarks=np.zeros(1)) for _ in range(self.n_faces)]

    def embed(self, img, face):
        self.embed_calls += 1
        return np.array([1.0, 0.0, 0.0, 0.0], np.float32)

    def warmup(self):
        pass


def client_with(n_faces: int, **settings) -> tuple[TestClient, FakeBackend]:
    backend = FakeBackend(n_faces)
    return TestClient(create_app(Settings(**settings), backend=backend)), backend


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
