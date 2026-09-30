"""Seed the MOCK external systems for flight POC001 from local, consenting volunteers' photos.

Reads data/volunteers/<profile_id>/reference.jpg (see data/README-consent.md) and:
  - uploads each reference image to mock-idp (the identity anchor),
  - puts a manifest for the flight into dcs-stub, plus one passenger (P900) who never consents.
data/volunteers/<id>/reference_twin.jpg, if present, is enrolled as a SECOND profile "<id>-TWIN": a labelled
SIMULATION of look-alikes (same person, two identities) for the AMBIGUOUS demo scene.

Resets the mocks first (profiles, manifests and any injected faults). Step 3 extends this script to create consents
and build the gallery in the gate-service.
Usage: python tools/seed.py [--mocks http://localhost:8002] [--flight POC001]
"""
import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
NON_CONSENTED = "P900"  # on the manifest but never consents: must never reach the gallery


def discover_profiles(data_dir: Path) -> dict[str, Path]:
    profiles = {}
    for folder in sorted((data_dir / "volunteers").glob("*")):
        if (folder / "reference.jpg").exists():
            profiles[folder.name] = folder / "reference.jpg"
        if (folder / "reference_twin.jpg").exists():
            profiles[f"{folder.name}-TWIN"] = folder / "reference_twin.jpg"
    return profiles


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mocks", default="http://localhost:8002")
    ap.add_argument("--flight", default="POC001")
    ap.add_argument("--data", type=Path, default=ROOT / "data")
    args = ap.parse_args()

    profiles = discover_profiles(args.data)
    if not profiles:
        print(f"WARNING: no photos in {args.data / 'volunteers'}; seeding the manifest with {NON_CONSENTED} only")

    with httpx.Client(base_url=args.mocks, timeout=10) as c:
        c.post("/admin/reset").raise_for_status()
        for pid, path in profiles.items():
            c.put(f"/admin/profiles/{pid}/reference-image", content=path.read_bytes(),
                  headers={"Content-Type": "image/jpeg"}).raise_for_status()
        passengers = [{"profile_id": pid, "seat": f"{10 + i}A"} for i, pid in enumerate(profiles)]
        passengers.append({"profile_id": NON_CONSENTED, "seat": "30C"})
        departure = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(hours=2)
        c.put(f"/admin/flights/{args.flight}/manifest", json={
            "flight_id": args.flight, "departure_time": departure.isoformat(), "passengers": passengers,
        }).raise_for_status()

    print(f"seeded {args.flight}: departure {departure.isoformat()}, "
          f"{len(profiles)} profiles with reference images {sorted(profiles)}, manifest size {len(passengers)} "
          f"(incl. non-consenting {NON_CONSENTED})")


if __name__ == "__main__":
    main()
