"""Scripted 2-minute demo (docs/demo-script.md), run as: python tools/gate_sim.py --scenario demo

Needs model-service :8001, mocks :8002, gate-service :8081 and the compose DB. For the purge scene, start the
gate-service with a short retention:  $env:GATE_RETENTION_GRACE="10s"; $env:GATE_RETENTION_PURGE_INTERVAL="5s"
Shows decisions, scores and counts only: no faces, no images.
"""
import subprocess
import sys
import time
import uuid
from pathlib import Path

import httpx

from photos import clean_jpeg, discover_volunteers, find_profile

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "-f", str(ROOT / "ops" / "docker-compose.yml"), "exec", "-T", "db",
           "psql", "-U", "poc", "-d", "gate", "-At", "-c"]
RESET_SQL = "TRUNCATE flight, passenger_consent, gallery_entry, gate_event, outbox, audit_log"


class Demo:
    def __init__(self, gate_url: str, mocks_url: str, data: Path):
        self.gate = httpx.Client(base_url=gate_url, timeout=10)
        self.mocks = httpx.Client(base_url=mocks_url, timeout=10)
        self.data = data

    def scene(self, title: str) -> None:
        print(f"\n=== {title}")

    def capture(self, profile: str, probe: int, flight: str = "POC001", gate: str = "G1") -> dict:
        path = find_profile(self.data, profile).probes[probe - 1]
        t = time.perf_counter()
        r = self.gate.post(f"/v1/gates/{gate}/events", data={"flight_id": flight},
                           files={"image": ("capture.jpg", clean_jpeg(path), "image/jpeg")},
                           headers={"Idempotency-Key": str(uuid.uuid4())})
        body = r.json() if r.status_code == 200 else {"decision": "MANUAL", "reason": f"HTTP_{r.status_code}"}
        ms = round((time.perf_counter() - t) * 1000)
        top = " ".join(f"{k}={body[k]:.3f}" for k in ("top1", "top2") if body.get(k) is not None)
        print(f"  {flight} {gate} {profile:9s} probe {probe} -> {body['decision']:6s} {body['reason']:18s} "
              f"profile={body.get('profile_id') or '-':6s} {top} ({ms} ms)")
        return body

    def gallery_size(self, flight: str = "POC001") -> str:
        f = self.gate.get(f"/v1/flights/{flight}").json()
        return f"{flight} {f['status']}: {f['active_entries']} matchable, {f['boarded_entries']} boarded"

    def breaker(self) -> str:
        return self.gate.get("/actuator/circuitbreakers").json()["circuitBreakers"]["identity"]["state"]

    def run(self) -> None:
        self.scene("0. Reset: empty database, no faults, seed flights POC001 + POC002")
        print("  " + subprocess.run(COMPOSE + [RESET_SQL], capture_output=True, text=True, check=True).stdout.strip())
        self.mocks.post("/admin/faults", json={}).raise_for_status()
        subprocess.run([sys.executable, str(ROOT / "tools" / "seed.py")], check=True, stdout=subprocess.DEVNULL)
        print("  " + self.gallery_size())

        self.scene("1. Only this flight's consented gallery; a hard capture goes to a human")
        self.capture("P001", 2)   # night, low angle: below T -> manual
        self.capture("P001", 1)   # daylight, frontal -> BOARD
        print("  " + self.gallery_size())

        self.scene("2. Boarded passengers leave the gallery; a stranger leaves no identity")
        self.capture("P001", 1)
        self.capture("STRANGER", 1)

        self.scene("3. Consent is checked at query time: withdraw S002, then S002 at the gate")
        self.gate.delete("/v1/consents/S002").raise_for_status()
        self.capture("S002", 1)

        self.scene("4. Identity provider slow (2 s): fast MANUAL, then the breaker opens")
        self.mocks.post("/admin/faults", json={"latency_ms": 2000}).raise_for_status()
        synthetic = [v.profile_id for v in discover_volunteers(self.data, "synthetic")
                     if v.profile_id.startswith("S") and v.profile_id not in ("S002", "S035")]
        queue = iter(synthetic[1:])
        rejected_fast = 0
        while rejected_fast < 2:
            was_open = self.breaker() == "OPEN"
            if was_open:
                print("  (breaker OPEN: the next call is rejected without contacting the identity provider)")
            body = self.capture(next(queue), 1)
            rejected_fast += was_open and body["reason"] == "IDP_UNAVAILABLE"
        print(f"  breaker: {self.breaker()}")
        self.mocks.post("/admin/faults", json={}).raise_for_status()
        print("  fault cleared; waiting 10 s (open state) ...")
        time.sleep(10.5)
        boarded = 0
        while boarded < 2:
            boarded += self.capture(next(queue), 1)["decision"] == "BOARD"
        print(f"  breaker: {self.breaker()}")

        self.scene("5. Look-alikes (SIMULATED: a re-cropped copy of P001's own reference) on flight POC002")
        self.capture("P001", 1, flight="POC002", gate="G2")

        self.scene("6. Departure: crypto-shred after the grace period")
        self.gate.post("/v1/flights/POC001/depart").raise_for_status()
        for _ in range(60):
            if self.gate.get("/v1/flights/POC001").json()["status"] == "PURGED":
                break
            time.sleep(1)
        print("  " + self.gallery_size())
        dek = subprocess.run(COMPOSE + ["SELECT coalesce(length(wrapped_dek)::text, 'NULL') FROM flight "
                                        "WHERE flight_id = 'POC001'"], capture_output=True, text=True).stdout.strip()
        print(f"  wrapped DEK bytes: {dek}  (NULL = key destroyed; templates unreadable, even from copies)")
        self.capture("S010", 2)
        print("\nThis is a toy: see README 'Production gaps'.")


def run_demo(gate_url: str, mocks_url: str, data: Path) -> None:
    Demo(gate_url, mocks_url, data).run()
