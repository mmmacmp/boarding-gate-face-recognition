# Two-minute demo (a recording, not a live demo)

Run: `python tools/gate_sim.py --scenario demo` (≈ 40 s; resets the database first). Start the gate-service with
`$env:GATE_RETENTION_GRACE="10s"; $env:GATE_RETENTION_PURGE_INTERVAL="5s"` so the purge scene completes.

**Recording rules:** terminal and diagrams only. Never show synthetic or volunteer face images: scores and counts
only. Label the look-alike scene as a simulation.

| Time | Scene (scenario output) | Say |
|---|---|---|
| 0:00–0:15 | Architecture diagram (README §3) | "Airline gate service, a stateless model service standing in for a vendor, and a mock identity provider." |
| 0:15–0:35 | 0–1: reset, gallery of 49; night capture → `NO_MATCH` (0.420 < T); daylight → `BOARD` 0.604; 48 matchable | "We match only against this flight's consented gallery. A poor capture goes to a human; once boarded, you're out of it." |
| 0:35–0:50 | 2: same passenger again → `NO_MATCH`; stranger → `NO_MATCH`, `profile=-` | "Re-boarding is impossible, and strangers leave no identity behind." |
| 0:50–1:00 | 3: withdraw S002 → `NO_MATCH` | "Consent is checked at query time, not only at enrolment." |
| 1:00–1:30 | 4: 2 s identity-provider latency → `IDP_UNAVAILABLE` (~400 ms) ×4 → breaker `OPEN` → rejected in ~100 ms → fault cleared → `BOARD` ×2 → `CLOSED` | "Any failure means manual boarding, fast, so the queue keeps moving." |
| 1:30–1:40 | 5: POC002 simulated look-alike → `AMBIGUOUS` 0.604 / 0.602 | "Close top-two scores go to a human, not a guess." (Mention the README finding: with a better-quality look-alike reference the gate boarded the wrong profile.) |
| 1:40–2:00 | 6: depart → `PURGED`, wrapped DEK `NULL`, gate event → `GALLERY_UNAVAILABLE`; README production gaps | "Templates are crypto-shredded after departure. And this is a toy; here's what production needs." |
