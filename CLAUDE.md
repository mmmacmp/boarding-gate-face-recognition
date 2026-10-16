# CLAUDE.md: Boarding-Gate Face Recognition POC (draft, 30 Sep 2026)

This is a two-day **personal prototype**: a boarding-gate face-recognition system built to explore the engineering around a matcher (gallery scoping, consent, fail-safe fallback, retention). It is presented with a recording, not a live demo. It is not affiliated with any airline or government agency.

The POC is a **consent-first flight-gallery boarding gate**:
- The airline gate service matches a passenger 1:N against **only that flight's consented, enrolled gallery**.
- Identity is anchored in a **mock external government identity service** (`mock-idp`).
- A passenger is removed from the gallery once boarded.
- The passenger is sent to **manual check** when the top two scores are too close.
- On **any failure** (including `mock-idp` being slow or down) the gate falls back to **manual boarding**.
- Consent is enforced before matching or logging.
- Templates are encrypted, and are crypto-shredded after departure.

**It is a toy. Never imply production readiness.**

## Read first (in this order)
1. `docs/research/phase4-plan.md` §4: the full plan, architecture, failure modes, build plan, demo script, pitch.
2. `docs/research/phase3-handoff.md`: chosen building blocks, licences, measured CPU numbers.
3. `docs/research/phase3-smoke-tests.md`: environment set-up. Save results to `docs/research/smoke-test-results.md`. **Measured local results override any number in the research docs.**
4. `docs/research/phase1-handoff.md` and `phase2-handoff.md`: the problems (P13, P1, P14, P10, P19, P12) and the evidence behind them.

Do **not** edit anything in `docs/research/` except to add `smoke-test-results.md`.

## Stack (validated in Phase 3 unless marked)
- **gate-service:** Java 21, Spring Boot 4.1.1.
  - Spring MVC, Spring Data JPA (Hibernate), Flyway, PostgreSQL driver, Actuator.
  - `spring-boot-starter-aspectj`, `io.github.resilience4j:resilience4j-spring-boot4:2.4.0`, `micrometer-registry-prometheus`.
  - Tests: JUnit 5, Mockito, WireMock (or `MockRestServiceServer`), JaCoCo.
- **model-service:** Python 3.11, FastAPI, `insightface==2.0` (SCRFD-10G + ArcFace R50 `buffalo_l`), `opencv-python==5.0.0.93` (YuNet + SFace license-clean mode), pytest.
  - GPU: `onnxruntime-gpu[cuda,cudnn]==1.30.0` needs **CUDA 13**; use `==1.26.0` for CUDA 12. Call `onnxruntime.preload_dlls()` before creating sessions.
  - `DEVICE=cuda|cpu` and `MODEL=r50|sface` switches.
- **mocks:** Python FastAPI app with two routers: `mock_idp` (identity-authority stand-in: reference images, 1:1 `/v1/verify`, `/admin/faults` for latency, error rate and down) and `dcs_stub` (manifest, boarding-event sink).
- **PostgreSQL** in Docker (`postgres:17` or `pgvector/pgvector:pg17`). Run the model-service **natively** on Windows for GPU access.
- **gate-console** (should): React + Vite; polls the gate-service.
- **Day-1 checkpoint (17:30):** if the `DecisionPolicy` and `TemplateCrypto` tests are not green **and** one end-to-end BOARD does not work through `tools/gate_sim.py`:
  1. Rebuild gate-service in **FastAPI** behind the **same OpenAPI contract** and the same test cases.
  2. Move the Java code to `spikes/gate-service-java/`.
  3. Record the switch in ADR-0001.

## Non-negotiable constraints
- **Honesty.**
  - The README must contain "Not production-ready", a **Production gaps** section, illustrative-threshold caveats, and a GPU vs CPU latency table with hardware details.
  - No accuracy claims.
- **No impersonation.** No real airline or government-agency names, logos or branding in code, UI or API paths. Use `poc`, `mock-idp`, `dcs-stub`, and flight numbers like `POC001`.
- **Biometric data.**
  - Only photos of **consenting volunteers** go in `data/` (gitignored).
  - No web or celebrity images. Never commit photos, embeddings or keys.
  - Probes are processed in memory only.
  - Never log images or embeddings. Log identity (`profile_id`) **only for BOARD**.
- **Licences.** InsightFace pretrained weights are **non-commercial research only**. Keep the `MODEL=sface` + YuNet license-clean mode working and documented.
- **Fail-safe.** Any exception or timeout, and any open circuit breaker, produces `MANUAL` with a reason code. Never an error that blocks the gate.
- **Java must stay explainable line by line.** The user's main stack is Python/FastAPI, so every Java class must be one they can explain line by line.
  - About 800 lines of main code or fewer.
  - Constructor injection, Java `record` DTOs, Spring MVC (no WebFlux).
  - No Lombok, no Spring Modulith, no Kafka, no reactive code.
  - Prefer clarity over cleverness. Add a short comment on the *why* where Spring "magic" is involved (AOP proxies, `@Transactional` boundaries).
  - After each block, list the new classes and concepts in `docs/WALKTHROUGH.md` for the user to explain in their own words.
- **Tests.**
  - Write the tests for `DecisionPolicy` and `TemplateCrypto` **first**.
  - Every row of the failure-mode table (plan §4.2) needs a passing test.
  - Keep a JaCoCo report and pytest for the model-service.
- **Scope discipline.** Won't do: real integrations, Kubernetes, Kafka, walk-through video tracking, certified PAD, demographic evaluation, HSM.

## Architecture
```
gate-sim / webcam ──POST /v1/gates/{gateId}/events (image, flightId, Idempotency-Key)──▶ gate-service (Spring Boot)
gate-service ──RestClient + timeout + @CircuitBreaker──▶ model-service /v1/embed      (stateless, never sees identities)
gate-service ──RestClient 300 ms + @CircuitBreaker───▶ mock-idp /v1/verify           (identity-authority stand-in, fault injection)
gate-service ──JPA/Flyway──▶ PostgreSQL: flight, passenger_consent, gallery_entry(ciphertext, iv, model_id), gate_event, outbox
gate-service ──(should) outbox poller──▶ dcs-stub /v1/boarding-events
gate-service ──Actuator──▶ /actuator/health, /actuator/prometheus, /actuator/circuitbreakers
```

**Gate-event sequence.** Any exception returns `MANUAL` + reason.
1. Idempotency-Key already seen → return the stored decision.
2. Flight not `BOARDING` or gallery not loaded → `GALLERY_UNAVAILABLE`.
3. Embed → exactly one face, else `NO_FACE` / `MULTI_FACE`. Model failure → `MODEL_UNAVAILABLE`. (Should: `LOW_QUALITY` as capture feedback via eDifFIQA(T); `PAD_SUSPECT` via MiniFASNet.)
4. Search this flight's `gallery_entry` rows with status `ACTIVE`, **joined with `passenger_consent` status `ACTIVE` at query time**, with the same `model_id` as the probe. Take top-1 and top-2 cosine. **No plaintext cache** (decision 30 Sep 2026): the flight's DEK is unwrapped and the matchable rows are decrypted per gate event, so plaintext templates live only for one request.
5. `top1 < T` → `NO_MATCH`; `top1 − top2 < Δ` → `AMBIGUOUS`.
6. `mock-idp` confirm (300 ms, breaker, **no retry on the hot path**): failure or open breaker → `IDP_UNAVAILABLE`; `INVALID` → `IDP_REJECTED`.
7. **Outside** external calls, one `@Transactional` block:
   - `UPDATE gallery_entry SET status='BOARDED' WHERE id=? AND status='ACTIVE'` (0 rows → `ALREADY_BOARDED`);
   - insert `gate_event`;
   - (should) insert `outbox`.
8. Metrics: `gate_decisions_total{gate,decision,reason}`, `gate_decision_seconds`, `gate_match_top1_score`, `gate_match_margin`, plus Resilience4j metrics. Log one structured line with no biometrics.

**Crypto and retention**
- AES-256-GCM (`AES/GCM/NoPadding`), 96-bit random IV, 128-bit tag.
- AAD = `flightId|profileId|modelId`.
- Per-flight DEK, wrapped by a KEK from a local PKCS12 keystore (`data/kek.p12`, path and password from environment variables; a stand-in for KMS/HSM).
- `PurgeJob` (`@Scheduled`), after departure plus a grace period: delete gallery rows, **delete the wrapped DEK** (crypto-shred), write an audit row.
- A test must prove that decryption fails after the shred.
- Known limit, which must be documented: decrypted templates sit in JVM memory while a gate event is processed (no cache between events; the DEK is unwrapped per event).
- Gallery rebuild must preserve boarded state: it replaces only `ACTIVE` rows and never re-enrols a passenger who is already `BOARDED` on that flight.

**Default config (placeholders; calibrate and document)**
- Thresholds: R50 cosine `T=0.44` (set 30 Sep 2026 from `tools/score_analysis.py`: highest impostor top-1 0.413 + 0.02 on a 49-identity, mostly synthetic gallery; still illustrative; the original placeholder 0.35 let 13/100 impostors through); SFace `T=0.363` (OpenCV Zoo default, not analysed); margin `Δ=0.10` (placeholder).
- `mock-idp` timeouts: connect 100 ms, read 300 ms.
- `model-service` read timeout: 800 ms on GPU, 1500 ms on CPU.
- Breaker: sliding window 10, minimum 5 calls, 50% failure rate, slow-call threshold 300 ms, 10 s open, 2 half-open calls.

## Build plan (half-day blocks; details in plan §4.3)
- **Pre-work (≤ 2 h):** run smoke-test steps 0–3, 9, 10 and 11 → `docs/research/smoke-test-results.md`. Record the GPU model and VRAM. Collect consenting volunteer photos.
- **D1-AM (Python):** repo skeleton, Postgres via docker compose, model-service `/v1/embed` + pytest, mocks with fault injection, `tools/seed.py` (flight `POC001`, 4–6 volunteers, a labelled "twin" pair).
- **D1-PM (Java, checkpoint):** Spring Boot project, Flyway V1, `DecisionPolicy` (tests first), `TemplateCrypto` (tests first), gallery build, gate-event happy path. **17:30 checkpoint** (see Stack).
- **D2-AM:** timeouts and breakers with MANUAL fallbacks + WireMock tests, Idempotency-Key, conditional board update + concurrency test, consent withdraw, purge and crypto-shred + test, Micrometer metrics, JaCoCo.
- **D2-PM:** `tools/bench_latency.py` (GPU / CPU / CPU capped at 4 threads; p50 and p95; N=200, warm-up 20; include the per-event decrypt + search cost), `tools/gate_sim.py` demo scenarios, README, `docs/architecture.md`, ADRs 0001–0004, `docs/WALKTHROUGH.md` (**the user writes it**), then record the 2-minute video.
  - Then, only if time remains: React console → quality/PAD flags → outbox → CI → stretch.
- **Stretch (optional):** safe matcher upgrade.
  - `model_id` guard; a shadow gallery built with SFace from `mock-idp` reference images.
  - An async shadow decision that never affects the real one, recorded as `gate_shadow_agreement_total{agree}`.
  - A per-flight cut-over flag.
- **Drop order if behind:** stretch → CI → outbox → quality/PAD → React console. **Never drop:** fallback tests, the crypto-shred test, the production-gaps section.

## Repository layout
```
CLAUDE.md, README.md
docs/research/ (unchanged)   docs/architecture.md   docs/decisions/   docs/WALKTHROUGH.md   docs/demo-script.md
gate-service/ (Maven; src/main/java/poc/gate/{api,decision,gallery,crypto,clients,events,metrics,config})
model-service/ (app/, tests/, requirements.txt)
mocks/ (mock_idp, dcs_stub)
gate-console/ (React, should)
tools/ (gate_sim.py, bench_latency.py, seed.py)
ops/ (docker-compose.yml, prometheus.yml, grafana/ (should))
data/ (.gitignored: volunteer photos + consent note, kek.p12)
spikes/ (only if the Day-1 checkpoint triggers)
```

## Commands (fill in as they are created)
- Python env (once, repo root, Python 3.11): `py -3.11 -m venv .venv`; `.\.venv\Scripts\pip install -r model-service/requirements.txt -r mocks/requirements.txt -r tools/requirements.txt`; then swap ONNX Runtime as described at the top of `model-service/requirements.txt`; `cd model-service; python download_models.py`
- Postgres: `docker compose -f ops/docker-compose.yml up -d --wait db` (host port **5433**, db `gate`, test db `gate_test`, user/password `poc`; 5432 is taken by a local PostgreSQL service)
- model-service (GPU): `cd model-service; $env:DEVICE="cuda"; uvicorn app.main:app --port 8001` (also `MODEL=sface`, `CPU_THREADS=4`, `MIN_FACE_PX=80`); tests: `python -m pytest`
- mocks: `cd mocks; uvicorn main:app --port 8002`; tests: `python -m pytest`
- gate-service: `cd gate-service; .\mvnw.cmd spring-boot:run` (port **8081**: 8080 is taken by a local Apache httpd, don't touch it); unit tests: `.\mvnw.cmd test` (no DB); all tests: `.\mvnw.cmd verify` (needs the compose DB; JaCoCo report in `target/site/jacoco`)
- Synthetic passengers (once): `python tools/fetch_digiface_subset.py` (DigiFace-1M subset via HTTP range requests, non-commercial licence, local only)
- Demo reset (a rebuild deliberately keeps boarded state): `docker compose -f ops/docker-compose.yml exec -T db psql -U poc -d gate -c "TRUNCATE flight, passenger_consent, gallery_entry, gate_event, outbox, audit_log"`
- Seed and scenarios: `python tools/seed.py`, then `python tools/gate_sim.py --scenario demo` (`--twin-from-photo` reproduces the reference-quality finding)
- Latency: `python tools/bench_latency.py --device cuda|cpu|cpu4`
- Demo console: `cd gate-console; npm install; npm run dev` → http://localhost:5173 (Vite proxies `/gate`, `/mocks`, `/model`; no CORS). It is the presenter-driven X-ray console for flight DEMO01 and needs demo mode:
  - once: `python tools/make_demo_assets.py` (S01-S03 + no-face / two-faces / tiny-face probes in gitignored `data/demo/`, verified with the model-service)
  - mocks with `$env:DEMO_XRAY="true"` (reference thumbnails); gate-service with `$env:SPRING_PROFILES_ACTIVE="demo"` (`application-demo.yml`: X-ray on, demo-only breaker window 2, 3 s purge grace)
  - `python tools/demo_scenario.py --reset-demo-flight` (DEMO01 only, ~10 s; the console's Reset button); rehearsal: `docs/demo-rehearsal.md`
  - X-ray screenshots show faces: keep them in gitignored `docs/img/xray/`; never commit media with the volunteer's face. Exception (author's choice, 16 Oct 2026): the demo video with the author's own face is hosted as a GitHub attachment and embedded in the README, never committed

## Definition of done
- Every row of the failure-mode table is green in tests.
- The demo scenarios run from `tools/gate_sim.py`.
- The README has the latency table (GPU and CPU, with hardware) and the production-gaps section.
- The 2-minute recording exists.
- The user can walk through every Java class without notes.

## How to work with the user
- Small, reviewable steps. After each block, summarise what changed and which Spring or Java concepts appeared, so the user can learn them.
- When a design choice touches an unknown airline internal, do not assume it: say "not publicly known" and add it to the open questions (plan §4.9).
- Use the Phase 3 building blocks, and prefer them over new dependencies. Ask before adding any new library.
