"""Seed the MOCK external systems and the gate-service for the demo flights.

Flights:
  POC001  main flight: consenting volunteers (data/volunteers/) + synthetic passengers (data/synthetic/, DigiFace-1M,
          non-commercial research licence) + P900 (on the manifest, never consents, must never reach the gallery).
          data/synthetic/STRANGER is never enrolled anywhere: the "stranger" at the gate.
  POC002  twin demo flight: the first volunteer + "<id>-TWIN", a SIMULATED look-alike whose reference is a re-cropped,
          re-compressed copy of the volunteer's own reference, so the margin rule deterministically says AMBIGUOUS.
          --twin-from-photo uses reference_twin.* instead (reproduces the README finding: a phone-quality reference
          beats the true ID-document reference, so the gate boards the wrong profile).

Every reference is EXIF-stripped before upload and, if the model-service is reachable, checked for exactly one face
>= 150 px. Resets the mocks first. If the gate-service is reachable, records consents and builds both galleries.
  DEMO01  presenter demo flight (5 passengers): P001, P001-TWIN (simulated) and S01-S03 from data/demo/
          (tools/make_demo_assets.py). Also uploads face-centred thumbnails to mock-idp for the X-ray console.
Usage: python tools/seed.py [--twin-from-photo] [--no-synthetic] [--demo-only]
"""
import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path

import cv2
import httpx
import numpy as np

from photos import REFERENCE_MIN_FACE_PX, clean_jpeg, discover_volunteers, simulated_lookalike

ROOT = Path(__file__).resolve().parents[1]
MAIN_FLIGHT, TWIN_FLIGHT, DEMO_FLIGHT = "POC001", "POC002", "DEMO01"
NON_CONSENTED = "P900"


def check_face(model_service: httpx.Client | None, label: str, data: bytes, min_px: int | None,
               quiet: bool = False) -> bool:
    """Print face count and width; warn and return False when an image would not work as an enrolment photo."""
    if model_service is None:
        return True
    faces = model_service.post("/v1/embed", content=data, headers={"Content-Type": "image/jpeg"}).json()["faces"]
    widths = [round(f["bbox"][2] - f["bbox"][0]) for f in faces]
    problem = ""
    if len(faces) != 1:
        problem = f"  <-- WARNING: expected exactly 1 face, found {len(faces)}"
    elif min_px and widths[0] < min_px:
        problem = f"  <-- WARNING: face {widths[0]} px < {min_px} px minimum"
    if problem or not quiet:
        print(f"  {label:36s} faces={len(faces)} width_px={widths}{problem}")
    return not problem


def connect_model_service(url: str) -> httpx.Client | None:
    client = httpx.Client(base_url=url, timeout=10)
    try:
        client.get("/health").raise_for_status()
        return client
    except httpx.HTTPError:
        print(f"model-service not reachable at {url}: face checks skipped")
        return None


def put_manifest(mocks: httpx.Client, flight_id: str, profile_ids: list[str]) -> None:
    departure = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(hours=2)
    passengers = [{"profile_id": pid, "seat": f"{10 + i // 6}{'ABCDEF'[i % 6]}"} for i, pid in enumerate(profile_ids)]
    mocks.put(f"/admin/flights/{flight_id}/manifest", json={
        "flight_id": flight_id, "departure_time": departure.isoformat(), "passengers": passengers,
    }).raise_for_status()
    shown = profile_ids if len(profile_ids) <= 6 else profile_ids[:3] + ["..."] + profile_ids[-2:]
    print(f"{flight_id}: departure {departure.isoformat()}, manifest of {len(profile_ids)}: {shown}")


def thumbnail(model_service: httpx.Client, data: bytes) -> bytes:
    """240 px square crop around the detected face (the reference itself stays at the identity provider)."""
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    h, w = img.shape[:2]
    faces = model_service.post("/v1/embed", content=data, headers={"Content-Type": "image/jpeg"}).json()["faces"]
    x1, y1, x2, y2 = faces[0]["bbox"] if faces else (0, 0, w, h)
    side = int(min(max(x2 - x1, y2 - y1) * 1.7, w, h))
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    left = int(min(max(cx - side / 2, 0), w - side))
    top = int(min(max(cy - side / 2, 0), h - side))
    crop = cv2.resize(img[top:top + side, left:left + side], (240, 240), interpolation=cv2.INTER_AREA)
    return cv2.imencode(".jpg", crop, [cv2.IMWRITE_JPEG_QUALITY, 88])[1].tobytes()


def seed_demo_flight(mocks: httpx.Client, model_service: httpx.Client, gate_url: str, data_dir: Path) -> None:
    """DEMO01: exactly 5 passengers for the presenter-driven X-ray console. Safe to re-run (no mock-idp reset)."""
    p001 = next((v for v in discover_volunteers(data_dir) if v.profile_id == "P001" and v.reference), None)
    demo = [v for v in discover_volunteers(data_dir, "demo") if v.reference]
    if p001 is None or len(demo) != 3:
        print("DEMO01 skipped: needs data/volunteers/P001 and data/demo/S01-S03 (python tools/make_demo_assets.py)")
        return
    references = {"P001": clean_jpeg(p001.reference), "P001-TWIN": simulated_lookalike(p001.reference)}
    references |= {v.profile_id: clean_jpeg(v.reference) for v in demo}
    for pid, data in references.items():
        mocks.put(f"/admin/profiles/{pid}/reference-image", content=data,
                  headers={"Content-Type": "image/jpeg"}).raise_for_status()
        mocks.put(f"/admin/profiles/{pid}/status", json={"status": "VALID"}).raise_for_status()
        mocks.put(f"/admin/profiles/{pid}/thumbnail", content=thumbnail(model_service, data)).raise_for_status()
    put_manifest(mocks, DEMO_FLIGHT, list(references))
    seed_gate_service(gate_url, list(references), [DEMO_FLIGHT])


def seed_gate_service(url: str, consenting: list[str], flights: list[str]) -> None:
    with httpx.Client(base_url=url, timeout=120) as gate:
        try:
            gate.get("/actuator/health").raise_for_status()
        except httpx.HTTPError:
            print(f"gate-service not reachable at {url}: consents and gallery build skipped")
            return
        for pid in consenting:
            gate.post("/v1/consents", json={"profile_id": pid}).raise_for_status()
        print(f"consent ACTIVE for {len(consenting)} profiles (never {NON_CONSENTED})")
        for flight_id in flights:
            r = gate.post(f"/v1/flights/{flight_id}/gallery:build")
            print(f"{flight_id} gallery build: HTTP {r.status_code} {r.json()}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mocks", default="http://localhost:8002")
    ap.add_argument("--model-service", default="http://localhost:8001")
    ap.add_argument("--gate-service", default="http://localhost:8081")
    ap.add_argument("--data", type=Path, default=ROOT / "data")
    ap.add_argument("--twin-from-photo", action="store_true", help="use reference_twin.* instead of the simulation")
    ap.add_argument("--no-synthetic", action="store_true")
    ap.add_argument("--demo-only", action="store_true", help="(re)seed only the 5-passenger DEMO01 flight")
    args = ap.parse_args()

    if args.demo_only:
        with httpx.Client(base_url=args.mocks, timeout=10) as mocks:
            seed_demo_flight(mocks, connect_model_service(args.model_service), args.gate_service, args.data)
        return

    volunteers = [v for v in discover_volunteers(args.data) if v.reference]
    synthetic = [] if args.no_synthetic else [v for v in discover_volunteers(args.data, "synthetic") if v.reference]
    if not volunteers:
        print(f"WARNING: no volunteers with a reference photo in {args.data / 'volunteers'}")
    model_service = connect_model_service(args.model_service)
    references: dict[str, bytes] = {}  # profile_id -> EXIF-free JPEG bytes

    print("checking reference photos (synthetic ones only if there is a problem):")
    for v in volunteers + synthetic:
        data = clean_jpeg(v.reference)
        ok = check_face(model_service, f"{v.profile_id} {v.reference.name}", data, REFERENCE_MIN_FACE_PX,
                        quiet=v in synthetic)
        if not ok and v in synthetic:
            print(f"  -> {v.profile_id} excluded: synthetic references must pass the enrolment check")  # e.g. S035
            continue
        references[v.profile_id] = data
    synthetic = [v for v in synthetic if v.profile_id in references]

    twins = []
    if volunteers:
        original = volunteers[0]
        twin_id = f"{original.profile_id}-TWIN"
        if args.twin_from_photo and original.twin_reference:
            references[twin_id] = clean_jpeg(original.twin_reference)
            label = f"{twin_id} ({original.twin_reference.name})"
        else:
            references[twin_id] = simulated_lookalike(original.reference)
            label = f"{twin_id} (SIMULATED look-alike)"
        check_face(model_service, label, references[twin_id], REFERENCE_MIN_FACE_PX)
        twins = [original.profile_id, twin_id]

    with httpx.Client(base_url=args.mocks, timeout=10) as mocks:
        mocks.post("/admin/reset").raise_for_status()
        for pid, data in references.items():
            mocks.put(f"/admin/profiles/{pid}/reference-image", content=data,
                      headers={"Content-Type": "image/jpeg"}).raise_for_status()
        put_manifest(mocks, MAIN_FLIGHT, [v.profile_id for v in volunteers + synthetic] + [NON_CONSENTED])
        if twins:
            put_manifest(mocks, TWIN_FLIGHT, twins)
        print(f"mock-idp: {len(references)} reference images (EXIF stripped)")
        seed_gate_service(args.gate_service, sorted(references), [MAIN_FLIGHT] + ([TWIN_FLIGHT] if twins else []))
        if model_service is not None:
            seed_demo_flight(mocks, model_service, args.gate_service, args.data)


if __name__ == "__main__":
    main()
