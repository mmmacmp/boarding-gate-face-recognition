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
Usage: python tools/seed.py [--twin-from-photo] [--no-synthetic]
"""
import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx

from photos import REFERENCE_MIN_FACE_PX, clean_jpeg, discover_volunteers, simulated_lookalike

ROOT = Path(__file__).resolve().parents[1]
MAIN_FLIGHT, TWIN_FLIGHT = "POC001", "POC002"
NON_CONSENTED = "P900"


def check_face(model_service: httpx.Client | None, label: str, data: bytes, min_px: int | None,
               quiet: bool = False) -> None:
    """Print face count and width; warn when an image would not work as an enrolment photo."""
    if model_service is None:
        return
    faces = model_service.post("/v1/embed", content=data, headers={"Content-Type": "image/jpeg"}).json()["faces"]
    widths = [round(f["bbox"][2] - f["bbox"][0]) for f in faces]
    problem = ""
    if len(faces) != 1:
        problem = f"  <-- WARNING: expected exactly 1 face, found {len(faces)}"
    elif min_px and widths[0] < min_px:
        problem = f"  <-- WARNING: face {widths[0]} px < {min_px} px minimum"
    if problem or not quiet:
        print(f"  {label:36s} faces={len(faces)} width_px={widths}{problem}")


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
    args = ap.parse_args()

    volunteers = [v for v in discover_volunteers(args.data) if v.reference]
    synthetic = [] if args.no_synthetic else [v for v in discover_volunteers(args.data, "synthetic") if v.reference]
    if not volunteers:
        print(f"WARNING: no volunteers with a reference photo in {args.data / 'volunteers'}")
    model_service = connect_model_service(args.model_service)
    references: dict[str, bytes] = {}  # profile_id -> EXIF-free JPEG bytes

    print("checking reference photos (synthetic ones only if there is a problem):")
    for v in volunteers + synthetic:
        references[v.profile_id] = clean_jpeg(v.reference)
        check_face(model_service, f"{v.profile_id} {v.reference.name}", references[v.profile_id],
                   REFERENCE_MIN_FACE_PX, quiet=v in synthetic)

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


if __name__ == "__main__":
    main()
