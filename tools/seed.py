"""Seed the MOCK external systems from local, consenting volunteers' photos (see data/README-consent.md).

Flights:
  POC001  main demo flight: every volunteer + P900 (on the manifest, never consents, must never reach the gallery)
  POC002  twin demo flight: each volunteer that has reference_twin.* is also enrolled as "<id>-TWIN", a labelled
          SIMULATION of look-alikes (same person, two identities). Kept off POC001 so normal boarding stays clean.

For every reference image: EXIF is stripped (decode + re-encode) before upload, and, if the model-service is
reachable, the face is checked (exactly one face, width >= 150 px). Probes are checked too, for information.
Resets the mocks first (profiles, manifests and injected faults). If the gate-service is reachable, it then records
consent for every enrolled profile (never P900) and builds the gallery of each flight.
Usage: python tools/seed.py [--mocks http://localhost:8002] [--model-service http://localhost:8001]
                            [--gate-service http://localhost:8081]
"""
import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx

from photos import REFERENCE_MIN_FACE_PX, clean_jpeg, discover_volunteers

ROOT = Path(__file__).resolve().parents[1]
MAIN_FLIGHT, TWIN_FLIGHT = "POC001", "POC002"
NON_CONSENTED = "P900"


def check_face(model_service: httpx.Client | None, label: str, data: bytes, min_px: int | None) -> None:
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
    print(f"  {label:32s} faces={len(faces)} width_px={widths}{problem}")


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
    passengers = [{"profile_id": pid, "seat": f"{10 + i}A"} for i, pid in enumerate(profile_ids)]
    mocks.put(f"/admin/flights/{flight_id}/manifest", json={
        "flight_id": flight_id, "departure_time": departure.isoformat(), "passengers": passengers,
    }).raise_for_status()
    print(f"{flight_id}: departure {departure.isoformat()}, manifest {profile_ids}")


def seed_gate_service(url: str, consenting: list[str], flights: list[str]) -> None:
    with httpx.Client(base_url=url, timeout=60) as gate:
        try:
            gate.get("/actuator/health").raise_for_status()
        except httpx.HTTPError:
            print(f"gate-service not reachable at {url}: consents and gallery build skipped")
            return
        for pid in consenting:
            gate.post("/v1/consents", json={"profile_id": pid}).raise_for_status()
        print(f"consent ACTIVE for {consenting} (never {NON_CONSENTED})")
        for flight_id in flights:
            r = gate.post(f"/v1/flights/{flight_id}/gallery:build")
            print(f"{flight_id} gallery build: HTTP {r.status_code} {r.json()}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mocks", default="http://localhost:8002")
    ap.add_argument("--model-service", default="http://localhost:8001")
    ap.add_argument("--gate-service", default="http://localhost:8081")
    ap.add_argument("--data", type=Path, default=ROOT / "data")
    args = ap.parse_args()

    volunteers = discover_volunteers(args.data)
    if not volunteers:
        print(f"WARNING: no volunteers in {args.data / 'volunteers'}")
    model_service = connect_model_service(args.model_service)
    references: dict[str, bytes] = {}  # profile_id -> EXIF-free JPEG bytes

    print("checking photos:")
    for v in volunteers:
        if v.reference is None:
            print(f"  {v.profile_id}: no reference.* photo, skipped")
            continue
        references[v.profile_id] = clean_jpeg(v.reference)
        check_face(model_service, f"{v.profile_id} {v.reference.name}", references[v.profile_id], REFERENCE_MIN_FACE_PX)
        if v.twin_reference:
            references[f"{v.profile_id}-TWIN"] = clean_jpeg(v.twin_reference)
            check_face(model_service, f"{v.profile_id}-TWIN {v.twin_reference.name}",
                       references[f"{v.profile_id}-TWIN"], REFERENCE_MIN_FACE_PX)
        for probe in v.probes:
            check_face(model_service, f"{v.profile_id} {probe.name} (probe)", clean_jpeg(probe), None)

    with httpx.Client(base_url=args.mocks, timeout=10) as mocks:
        mocks.post("/admin/reset").raise_for_status()
        for pid, data in references.items():
            mocks.put(f"/admin/profiles/{pid}/reference-image", content=data,
                      headers={"Content-Type": "image/jpeg"}).raise_for_status()
        people = [pid for pid in references if not pid.endswith("-TWIN")]
        twins = [pid for pid in references if pid.endswith("-TWIN")]
        put_manifest(mocks, MAIN_FLIGHT, people + [NON_CONSENTED])
        if twins:
            put_manifest(mocks, TWIN_FLIGHT, [t.removesuffix("-TWIN") for t in twins] + twins)
    print(f"mock-idp reference images: {sorted(references)} (EXIF stripped)")
    seed_gate_service(args.gate_service, sorted(references), [MAIN_FLIGHT] + ([TWIN_FLIGHT] if twins else []))


if __name__ == "__main__":
    main()
