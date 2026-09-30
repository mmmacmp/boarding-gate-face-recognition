# Architecture

Interview POC, not production-ready. External systems are mocks with hypothetical APIs.

## Components

| Component | Tech | Responsibility | Owns data? |
|---|---|---|---|
| gate-service `:8081` | Java 21 (built on JDK 25), Spring Boot 4.1.1, Spring MVC, JPA/Hibernate, Flyway, Resilience4j, Micrometer | Airline business logic: consent, gallery build, 1:N decision, fallback, idempotency, encryption, retention, metrics | Yes (PostgreSQL) |
| model-service `:8001` | Python 3.11, FastAPI, InsightFace 2.0 (SCRFD-10G + ArcFace R50) or OpenCV YuNet + SFace; ONNX Runtime GPU/CPU | Stateless detect + embed; a stand-in for a vendor SDK; never sees identities | No |
| mock-idp `:8002` | Python FastAPI (router) | Identity-provider stand-in: reference images, 1:1 `verify`, fault injection | In-memory mock |
| dcs-stub `:8002` | Python FastAPI (router) | Manifest source, boarding-event sink (idempotent) | In-memory mock |
| gate-console `:5173` | React 19 + Vite 8, plain CSS | Demo console: gate panel (demo photos / webcam, decision banner, score bars) and operations panel (gallery counts, manual rate, breaker timeline, latency waterfall, faults, consent, reset). Dev-server proxy, so no CORS | No |
| PostgreSQL `:5433` | Docker `postgres:17` | `flight`, `passenger_consent`, `gallery_entry`, `gate_event`, `outbox` (unused), `audit_log` | — |
| tools | Python | `seed.py`, `gate_sim.py` (+ demo scenario), `bench_latency.py`, `score_analysis.py`, `fetch_digiface_subset.py` | — |

## Service contracts

| Service | Endpoint | Notes |
|---|---|---|
| gate-service | `POST /v1/gates/{gateId}/events` (multipart `image`, form `flight_id`, header `Idempotency-Key`) → `{event_id, decision, reason, profile_id?, top1, top2, created_at, timings{embed_ms, search_ms, identity_ms, db_ms}}` | `profile_id` only for BOARD; timings not stored |
| | `GET /v1/events` (newest 20), `GET /v1/policy` (thresholds per model, margin) | for the console |
| | DEMO ONLY (`demo.xray=true`, profile `demo`): `GET /v1/demo/flights/{id}/gallery` (pseudonymous ids, ACTIVE / BOARDED / CONSENT_WITHDRAWN / NOT_IN_GALLERY, is_synthetic), `POST /v1/demo/breakers/reset`, and an `xray` block `{face_boxes, scores{profile_id: cosine}}` in gate-event responses | 404 / absent by default (tested); never images or embeddings |
| | `POST /v1/consents {profile_id}`, `DELETE /v1/consents/{id}` | withdrawal deletes matchable templates |
| | `POST /v1/flights/{id}/gallery:build`, `POST /v1/flights/{id}/depart`, `GET /v1/flights/{id}` | 503 if a dependency fails during build; 409 for a departed flight |
| model-service | `POST /v1/embed` (raw JPEG/PNG bytes) → `{model_id, dim, faces:[{bbox, det_score, embedding?}], small_faces, timings_ms}`; `GET /health`; `GET /metrics`; `POST /admin/faults {down, latency_ms}` (demo only) | embedding only when exactly one face ≥ `MIN_FACE_PX`; only small faces → gate `LOW_QUALITY` |
| mock-idp | DEMO ONLY (`DEMO_XRAY=true`): `GET /v1/demo/profiles/{id}/thumbnail` (reference thumbnails for the X-ray console come from the identity provider, never from the gate-service); `GET /v1/profiles/{id}/reference-image`; `POST /v1/verify {profile_id, flight_id}` → `VALID\|INVALID`; `POST /admin/faults {latency_ms, error_rate, down}` | hypothetical |
| dcs-stub | `GET /v1/flights/{id}/manifest`; `POST /v1/boarding-events` | hypothetical |

## Gate-event sequence

```
gate_sim            gate-service                              model-service   mock-idp      PostgreSQL
   │ POST event ──▶ │ Idempotency-Key seen? ─────────────────────────────────────────────▶ │ (return stored)
   │                │ flight BOARDING? ───────────────────────────────────────────────────▶ │
   │                │ embed(image) ───────────────────────────▶ │ (800 ms, breaker)
   │                │ ◀── model_id, faces[, embedding]          │
   │                │ matchable rows (ACTIVE ⋈ consent ACTIVE, same model_id) ────────────▶ │
   │                │ unwrap DEK, decrypt rows, cosine top-2 (in memory, per event)          │
   │                │ DecisionPolicy: T, Δ, reason codes                                      │
   │                │ verify(profile) ───────────────────────────────────────▶ │ (300 ms, breaker, no retry)
   │                │ @Transactional: UPDATE … WHERE status='ACTIVE' + INSERT gate_event ──▶ │
   │ ◀── decision   │ metrics + one log line (profile only for BOARD)
```

Any exception or open breaker → `MANUAL` with a reason code. Database down → HTTP 503 with a `MANUAL` body.

## Data and keys

- `gallery_entry`: `iv` (12 B) + `ciphertext` (AES-256-GCM, 16 B tag), AAD `flight|profile|model`, `status`
  `ACTIVE|BOARDED`, `model_id`.
- `flight.wrapped_dek`: the per-flight DEK wrapped (AES key wrap) by the KEK from `data/kek.p12`.
- Retention: `depart` → after `gate.retention.grace` the `PurgeJob` deletes rows and nulls `wrapped_dek` in one
  transaction, and writes `audit_log`. Limit: the wrapped DEK shares the database (and its backups) with the
  ciphertext; production keeps DEKs in a KMS.

## Failure modes

See the table in the README §6; each row has a named test.

## Latency budget vs measured (GPU desktop, p95)

| Step | Budget (plan, target) | Measured |
|---|---|---|
| Embed (detect + embed, HTTP) | ≤ 300 ms | 24.6 ms |
| Gallery search incl. decrypt (49 entries) | ≤ 5 ms | 5.7 ms |
| Identity confirm | ≤ 300 ms (timeout) | local mock, part of end to end |
| End to end | ≤ 1 s | 55.7 ms |

CPU numbers and method: [`latency.md`](latency.md).
