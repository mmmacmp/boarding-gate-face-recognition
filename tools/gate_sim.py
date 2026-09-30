"""Gate camera simulator: posts volunteer probe photos to the gate-service, one gate event per photo.

  python tools/gate_sim.py --profile P001                 # all of P001's probes (probe.*, probe_02.*, ...)
  python tools/gate_sim.py --profile P001 --probe 2       # only the 2nd probe (e.g. the hard, night capture)
  python tools/gate_sim.py --image path/to/capture.jpg    # any consenting person's photo
  python tools/gate_sim.py --profile P001 --probe 1 --key same-key   # repeat an Idempotency-Key

EXIF is stripped before sending. A non-200 answer is shown as MANUAL: the gate's own default when the service fails.
(Scripted demo scenarios come in Step 5.)
"""
import argparse
import time
import uuid
from pathlib import Path

import httpx

from photos import clean_jpeg, discover_volunteers

ROOT = Path(__file__).resolve().parents[1]


def send(gate: httpx.Client, gate_id: str, flight_id: str, image: bytes, key: str) -> dict:
    t = time.perf_counter()
    try:
        r = gate.post(f"/v1/gates/{gate_id}/events", data={"flight_id": flight_id},
                      files={"image": ("capture.jpg", image, "image/jpeg")}, headers={"Idempotency-Key": key})
        body = r.json() if r.status_code == 200 else {"decision": "MANUAL", "reason": f"CLIENT_DEFAULT_HTTP_{r.status_code}"}
    except httpx.HTTPError as e:
        body = {"decision": "MANUAL", "reason": f"CLIENT_DEFAULT_{type(e).__name__}"}
    body["client_ms"] = round((time.perf_counter() - t) * 1000)
    return body


def fmt(score) -> str:
    return "-" if score is None else f"{score:.3f}"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--gate-service", default="http://localhost:8081")
    ap.add_argument("--gate", default="G1")
    ap.add_argument("--flight", default="POC001")
    ap.add_argument("--profile", help="volunteer folder name under data/volunteers")
    ap.add_argument("--probe", type=int, help="1-based probe number (default: all probes)")
    ap.add_argument("--image", type=Path, help="send this image instead of a volunteer probe")
    ap.add_argument("--key", help="Idempotency-Key (default: a new UUID per capture)")
    ap.add_argument("--data", type=Path, default=ROOT / "data")
    args = ap.parse_args()

    if args.image:
        captures = [args.image]
    elif args.profile:
        volunteer = next((v for v in discover_volunteers(args.data) if v.profile_id == args.profile), None)
        if volunteer is None or not volunteer.probes:
            raise SystemExit(f"no probes for {args.profile} in {args.data / 'volunteers'}")
        captures = volunteer.probes if args.probe is None else [volunteer.probes[args.probe - 1]]
    else:
        raise SystemExit("give --profile or --image")

    with httpx.Client(base_url=args.gate_service, timeout=10) as gate:
        for path in captures:
            r = send(gate, args.gate, args.flight, clean_jpeg(path), args.key or str(uuid.uuid4()))
            print(f"{args.flight} gate {args.gate} {path.name:16s} -> {r['decision']:6s} {r['reason']:22s} "
                  f"profile={r.get('profile_id') or '-':10s} top1={fmt(r.get('top1'))} top2={fmt(r.get('top2'))} "
                  f"({r['client_ms']} ms)")


if __name__ == "__main__":
    main()
