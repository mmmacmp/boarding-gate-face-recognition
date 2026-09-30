"""Real models on CPU. Photo-based tests are skipped until consenting volunteers' photos are in data/."""
import numpy as np
import pytest

from conftest import blank_image, volunteer_references


def post(client, data: bytes):
    r = client.post("/v1/embed", content=data, headers={"Content-Type": "image/jpeg"})
    assert r.status_code == 200, r.text
    return r.json()


def test_r50_blank_image_has_no_faces(r50_client):
    body = post(r50_client, blank_image())
    assert body["model_id"] == "r50-buffalo_l" and body["dim"] == 512
    assert body["faces"] == []


def test_r50_health_is_cpu(r50_client):
    assert r50_client.get("/health").json()["device"] == "cpu"


@pytest.mark.skipif(not volunteer_references(), reason="no volunteer photos in data/volunteers/")
@pytest.mark.parametrize("path", volunteer_references(), ids=lambda p: p.parent.name)
def test_r50_volunteer_reference_gives_one_normalised_512d_embedding(r50_client, path):
    body = post(r50_client, path.read_bytes())
    assert len(body["faces"]) == 1, "reference photos must contain exactly one face"
    emb = np.array(body["faces"][0]["embedding"])
    assert emb.shape == (512,)
    assert abs(np.linalg.norm(emb) - 1.0) < 1e-3


def test_sface_blank_image_has_no_faces(sface_client):
    body = post(sface_client, blank_image())
    assert body["model_id"] == "sface-2021dec" and body["dim"] == 128
    assert body["faces"] == []


@pytest.mark.skipif(not volunteer_references(), reason="no volunteer photos in data/volunteers/")
def test_sface_volunteer_reference_gives_one_128d_embedding(sface_client):
    body = post(sface_client, volunteer_references()[0].read_bytes())
    assert len(body["faces"]) == 1
    assert len(body["faces"][0]["embedding"]) == 128
