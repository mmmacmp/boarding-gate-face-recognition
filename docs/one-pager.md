# Consent-first flight-gallery boarding gate: one-page summary

*Two-day personal prototype · **not production-ready** · all external systems are mocks · thresholds are illustrative, no
accuracy claims.*

**Problem.** At an airport boarding gate, face recognition only works if the airline side gets the plumbing right:
match against a small, consented gallery (gallery size is the main accuracy lever), confirm identity with an external
identity provider, fail **fast to manual** when anything breaks, and never keep more biometric data than needed. The
POC shows that part: not the matcher, everything around it.

**Architecture.**

```
 gate camera / demo console ──POST event──▶ gate-service (Java 21, Spring Boot 4.1.1)
                                              │  consent-first per-flight gallery · DecisionPolicy (T, Δ)
                                              │  AES-256-GCM templates, per-flight key · breakers → MANUAL
             ┌────────────────────────────────┼──────────────────────────────┐
             ▼ 800 ms + breaker               ▼ 300 ms + breaker, no retry   ▼ JPA / Flyway
   model-service (Python, InsightFace)   mock-idp (identity provider)   PostgreSQL (ciphertext only)
   stateless, never sees identities      1:1 confirm, fault injection   crypto-shred after departure
```

Decision: exactly one face → 1:N over the flight's `ACTIVE` entries **joined with active consent at query time** →
`top1 < T` NO_MATCH · `top1 − top2 < Δ` AMBIGUOUS → 1:1 confirm → one transaction: board at most once + audit event.
Any failure or open breaker → `MANUAL` with a reason. Identity is logged only for `BOARD`.

**Key findings** (one desktop: i5-14400F, RTX 4060; 49-identity gallery, mostly synthetic DigiFace-1M faces, so this
shows the method, not operational numbers).

| Threshold (ArcFace R50) | Impostor searches ≥ T | Genuine → manual |
|---|---|---|
| 0.35 (placeholder) | **13 of 100** | 2 of 97 |
| **0.44** (max impostor 0.413 + 0.02) | 0 of 100 | **16 of 97** |

- Raising T trades automation for safety; 100 searches bound the false-match rate only at ~3% (rule of three).
- Reference quality decides which identity wins: the same probe scored 0.831 against a phone-quality look-alike
  reference vs 0.604 against the true ID-document reference, so the gate boarded the wrong profile. The margin
  rule only helps when scores are close.

| p50 / p95 (ms) | Model-service | End-to-end decision | Per-event decrypt + search |
|---|---|---|---|
| GPU | 23.6 / 24.6 | **50.9 / 55.7** | 2.6 / 5.7 |
| CPU, all threads | 363 / 458 | 457 / 565 | 26.7 / 45.6 |
| CPU, 4 threads (edge proxy) | 158 / 174 | **177 / 193** | 2.2 / 2.6 |

- A gate does not need a GPU for one passenger at a time; on a shared host, cap ONNX Runtime threads (the all-core
  default was 2–3× slower and slowed the JVM).
- Every failure mode (identity provider slow or down, model down, duplicate request, two gates at once, consent
  withdrawn, look-alikes, stranger, DB down) ends in a fast `MANUAL`, each proven by a test (57 gate-service tests).

**Production gaps (not exhaustive).** No certified matcher or PAD; thresholds not calibrated at a target FPIR on
operational data, no demographic evaluation · no real identity-provider/DCS contract, mTLS or legal review · KEK in a
file, not an HSM/KMS; the wrapped per-flight key shares the database and its backups, so a pre-purge backup stays
decryptable · plaintext templates in memory during an event · single instance, no HA/DR or peak-load test · "low
quality" is a face-size proxy · the audit trail has no subject reference.

**Questions it leads into.** Where does 1:N matching run today, and what is the policy when the identity provider
degrades mid-boarding? How are galleries scoped and thresholds monitored per gate? Who re-extracts templates when the
matcher is upgraded? What is retained after departure?
