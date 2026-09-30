import time

import pytest
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)
FAKE_IMAGE = b"\xff\xd8 not a real photo, the mock only stores bytes"


@pytest.fixture(autouse=True)
def clean_state():
    client.post("/admin/reset")
    client.put("/admin/profiles/P001/reference-image", content=FAKE_IMAGE, headers={"Content-Type": "image/jpeg"})
    yield
    client.post("/admin/reset")


def verify(profile_id="P001"):
    return client.post("/v1/verify", json={"profile_id": profile_id, "flight_id": "POC001"})


# --- mock-idp -------------------------------------------------------------------------------------------------

def test_reference_image_round_trip():
    r = client.get("/v1/profiles/P001/reference-image")
    assert r.status_code == 200 and r.content == FAKE_IMAGE and r.headers["content-type"] == "image/jpeg"


def test_unknown_profile_image_is_404():
    assert client.get("/v1/profiles/NOPE/reference-image").status_code == 404


def test_verify_valid_then_invalid():
    assert verify().json()["result"] == "VALID"
    client.put("/admin/profiles/P001/status", json={"status": "INVALID"})
    assert verify().json()["result"] == "INVALID"


def test_unknown_profile_verifies_invalid():
    assert verify("NOPE").json()["result"] == "INVALID"


def test_admin_list_has_no_images():
    assert client.get("/admin/profiles").json() == {"P001": "VALID"}


def test_latency_fault_delays_verify():
    client.post("/admin/faults", json={"latency_ms": 400})
    t = time.perf_counter()
    assert verify().status_code == 200
    assert time.perf_counter() - t >= 0.4 - 0.03  # Windows timer resolution is ~15 ms


def test_down_fault_returns_503_on_all_public_endpoints_but_admin_still_works():
    client.post("/admin/faults", json={"down": True})
    assert verify().status_code == 503
    assert client.get("/v1/profiles/P001/reference-image").status_code == 503
    assert client.get("/admin/faults").json()["down"] is True
    client.post("/admin/faults", json={})  # clear all faults
    assert verify().status_code == 200


def test_error_rate_one_always_fails():
    client.post("/admin/faults", json={"error_rate": 1.0})
    assert all(verify().status_code == 500 for _ in range(5))


def test_invalid_fault_values_rejected():
    assert client.post("/admin/faults", json={"error_rate": 2}).status_code == 422
    assert client.post("/admin/faults", json={"latency_ms": -1}).status_code == 422


# --- dcs-stub -------------------------------------------------------------------------------------------------

MANIFEST = {"flight_id": "POC001", "departure_time": "2026-10-01T10:00:00Z",
            "passengers": [{"profile_id": "P001", "seat": "12A"}, {"profile_id": "P900"}]}


def test_manifest_round_trip():
    assert client.put("/admin/flights/POC001/manifest", json=MANIFEST).status_code == 204
    body = client.get("/v1/flights/POC001/manifest").json()
    assert [p["profile_id"] for p in body["passengers"]] == ["P001", "P900"]


def test_unknown_flight_manifest_is_404():
    assert client.get("/v1/flights/NOPE/manifest").status_code == 404


def test_manifest_path_body_mismatch_is_400():
    assert client.put("/admin/flights/OTHER/manifest", json=MANIFEST).status_code == 400


def test_boarding_event_is_deduplicated_by_event_id():
    event = {"event_id": "e-1", "flight_id": "POC001", "profile_id": "P001", "gate_id": "G1",
             "boarded_at": "2026-10-01T09:30:00Z"}
    assert client.post("/v1/boarding-events", json=event).json() == {"accepted": True, "duplicate": False}
    assert client.post("/v1/boarding-events", json=event).json() == {"accepted": True, "duplicate": True}
    assert len(client.get("/v1/boarding-events", params={"flight_id": "POC001"}).json()) == 1


def test_faults_do_not_affect_dcs_stub():
    client.post("/admin/faults", json={"down": True})
    client.put("/admin/flights/POC001/manifest", json=MANIFEST)
    assert client.get("/v1/flights/POC001/manifest").status_code == 200


# --- demo X-ray thumbnails (DEMO ONLY, env DEMO_XRAY) ------------------------------------------------------------

def test_thumbnail_is_404_by_default(monkeypatch):
    monkeypatch.delenv("DEMO_XRAY", raising=False)
    client.put("/admin/profiles/P001/thumbnail", content=b"thumb")
    assert client.get("/v1/demo/profiles/P001/thumbnail").status_code == 404


def test_thumbnail_served_when_enabled_even_while_idp_is_down(monkeypatch):
    monkeypatch.setenv("DEMO_XRAY", "true")
    client.put("/admin/profiles/P001/thumbnail", content=b"thumb")
    client.post("/admin/faults", json={"down": True})
    r = client.get("/v1/demo/profiles/P001/thumbnail")
    assert r.status_code == 200 and r.content == b"thumb"
