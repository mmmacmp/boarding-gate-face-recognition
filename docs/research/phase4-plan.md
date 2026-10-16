# Facial Recognition at Airline Boarding Gates: Phase 4 POC Recommendation (as of 30 Sep 2026)

**Inputs (authoritative):** `phase1-handoff.md`, `phase2-handoff.md`, `phase3-handoff.md`, plus the full reports for detail. Before writing, I checked the three handoffs on disk; their checksums match the versions produced in Phases 1–3.

**Smoke-test results:** `docs/research/smoke-test-results.md` does not exist yet, and neither does the name the Phase 3 checklist used (`phase3-smoke-results.md`). So **no local GPU numbers exist**. Every latency below is either a Phase 3 cloud CPU reference (2 vCPU, no GPU, marked [M]) or a target (marked [I]). When you run `phase3-smoke-tests.md`, save the results as `docs/research/smoke-test-results.md`. Those results then override the numbers here.

**Freshness check (30 Sep 2026):** I found no new airline or identity-authority gate announcements beyond Phase 1. Gulf News (8 Sep 2026) repeats the figures already used: 745,188 Red Carpet users and 9,382,814 Smart Gate users (Jan–Aug 2026), 3.4 s average. [V] https://gulfnews.com/uae/dubais-biometric-red-carpet-processes-745188-travellers-in-eight-months-of-2026-1.500666738

**Labels:** [V] = VERIFIED (source given). [M] = measured in Phase 3 (cloud workspace, CPU only). [I] = INFERRED, a design choice or target. ⚠️OLD = source older than about 24 months. Every statement about the airline's internal architecture is **not publicly known**. The POC is a hypothetical design, and where this plan needs such a fact it turns the gap into an open question.

---

## TL;DR
- **Recommended idea:** **I1, the consent-first flight-gallery gate** (the baseline). The stretch goal is **safe matcher upgrade**: template versioning plus shadow scoring, taken from I3.
  - I1 covers 4 of the Phase 1 top-5 problems (P13 integration, P1 thresholds and galleries, P14 fallback, P10 consent) and also P19 (template security) and P12 (GDPR).
  - The core is plain backend engineering, which is where an airline IT team owns the design. [I]
- **Recommended stack:** a **Java 21 / Spring Boot 4.1.1 gate service** (business rules, consent, fallback, crypto, metrics) plus a **Python / FastAPI model service** (InsightFace SCRFD + ArcFace; standing in for a vendor SDK).
  - In the weighted matrix this ties with all-Python (both score 45/50).
  - The tie is broken on target-stack fit, learning value and a realistic vendor/airline boundary. There is also a **hard checkpoint at the end of Day 1**: if the Java core is not green, the gate service switches to FastAPI behind the same API contract.
- **What it proves:** the passenger is matched only against that flight's consented gallery, and boarded passengers drop out of it. Too-close top-two scores go to manual check. **Any failure goes to manual boarding**, including the mock identity authority being slow or down. Templates are encrypted and crypto-shredded after departure. Metrics show all of this.
- **What it is not:** it is not production-ready, not a certified matcher or PAD, not an airline or identity-authority interface, and not a performance claim. The README carries a "production gaps" section.

---

## 1. Context in five lines (from the handoffs)
1. The airline runs consent-based face recognition at DXB T3: AED 85m, 200+ cameras, "some" gates on Concourses A/B/C, loyalty-programme members 18+. Identity is anchored to **identity-authority profiles**. [V] https://www.emirates.com/media-centre/speedy-seamless-secure--emirates-invests-in-innovative-facial-recognition-technology-across-dubai-international-dxb/ (3 Nov 2025)
2. The airline tells non-users that biometric zones "may automatically recognize you", and keeps passport and boarding pass "available at every touchpoint". [V] https://www.emirates.com/us/english/experience/contactless-journey/biometrics/ (Oct 2025)
3. Gallery size drives 1:N errors. Across NIST FRTE algorithms, median FNIR goes from 0.61% (640k identities) to 10.34% (12M) at FPIR 0.003. Large systems shrink the candidate gallery, as CBP's TVS does. [V] https://www.biometricupdate.com/202609/new-nist-frte-1n-results-illustrate-facial-recognition-accuracys-multidimensionality (16 Sep 2026). In a flight gallery, an in-gallery false positive "may result in a false negative for the correct passenger". [V] https://nvlpubs.nist.gov/nistpubs/ir/2021/NIST.IR.8381.pdf (Jul 2021) ⚠️OLD
4. Degraded modes are real: EES suspended biometric capture at congested crossings in 2026, and the Collins MUSE outage forced manual check-in and boarding at four European airports (Sep 2025). [V] https://time.com/article/2026/08/12/eu-europe-travel-entry-exit-system-ees-passport-biometric/ (12 Aug 2026); https://www.abc.net.au/news/2025-09-21/cyber-attack-collins-aerospace-disrupts-europe-airports/105798264 (21 Sep 2025)
5. Privacy architecture matters. For EU passengers, the EDPB finds centralised 1:N templates controlled by an airline or airport incompatible with Art. 25, while passenger-held or passenger-keyed storage is compatible. CBP makes airline camera operators purge images after transmission. [V] https://www.edpb.europa.eu/system/files/documents/2024-05/edpb_opinion_202411_facialrecognitionairports_en.pdf (23 May 2024) ⚠️OLD but operative; https://www.federalregister.gov/documents/2025/10/27/2025-19655/collection-of-biometric-data-from-aliens-upon-entry-to-and-departure-from-the-united-states (27 Oct 2025)

---

## 2. POC ideas

Components shared by all ideas, from the Phase 3 handoff:
- Models: SCRFD-10G + ArcFace R50 (InsightFace 2.0, weights **non-commercial**), with YuNet + SFace (OpenCV Zoo, MIT/Apache-2.0) as the license-clean fallback. eDifFIQA(T) for quality (CC BY 4.0), MiniFASNet V2 for PAD (via UniFace), and roboflow/trackers ByteTrack.
- Search: exact inner-product search (NumPy/Java) or pgvector.
- Java libraries: Resilience4j 2.4.0 with `resilience4j-spring-boot4`, and Actuator + Micrometer.
- Python libraries: pybreaker, tenacity, Prometheus.
- Crypto: TenSEAL for homomorphic-encryption experiments.

### I1. Consent-first flight-gallery gate (baseline, **recommended**)

| Field | Content |
|---|---|
| Problems | **P13, P1, P14, P10** (4 of the top 5), plus P19, P12, P8 (look-alikes via the margin rule), and part of P15 (metrics) |
| What it demonstrates | You treat face recognition as **airline business logic around a vendor matcher**: gallery scoping, consent, fail-safe fallback, idempotent gate events, retention and observability. That is the part airline IT most plausibly owns (Phase 1 ownership analysis [I]) |
| Recommended stack | **Java/Spring Boot gate service + Python model service** (see §3–4 for the stack decision). The gate logic is plain CRUD, rules and resilience, which is the target enterprise stack. The ML stays in Python, where the models run and are validated [M] |
| Architecture | See the diagram in §4.2. React agent console, gate simulator, **gate-service** (Spring Boot 4.1.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, Resilience4j, Micrometer), PostgreSQL, **model-service** (FastAPI; InsightFace on GPU or CPU; stateless), and **mocks** (FastAPI): `mock-idp` standing in for the identity authority (profiles, reference images, 1:1 confirm, fault injection) and `dcs-stub` (manifest, boarded-state sink) |
| Phase 3 blocks | SCRFD-10G + ArcFace R50 (primary), YuNet + SFace (fallback / license-clean mode), exact search (Java), Resilience4j + Actuator/Micrometer/Prometheus, FastAPI for model service and mocks, PostgreSQL. Optional: eDifFIQA(T), MiniFASNet |
| Real vs stubbed | **Real:** face detection and embeddings, 1:N search, decision policy, consent filter, AES-256-GCM envelope encryption with per-flight keys, crypto-shredding, circuit breakers and timeouts, idempotency, metrics, tests. **Stubbed:** the identity authority (mock-idp, a hypothetical API), DCS, gate hardware (a CLI or webcam simulator), KMS/HSM (key-encryption key from a local keystore file), certified PAD, operational images (consenting volunteers), and threshold calibration (illustrative values) |
| **Must** (2 days) | Model service `/v1/embed`. Mock-idp and dcs-stub with latency/error/down switches. Gallery build from consented manifest passengers. Gate event flow: 1:N with boarded exclusion, threshold T, top-2 margin Δ, 1:1 confirm with mock-idp, **fallback to MANUAL on any failure**. Idempotency-Key. Conditional "board once" update. Consent withdraw. AES-GCM templates with purge and crypto-shred after departure. Actuator/Prometheus metrics. JUnit tests for policy, fallback, crypto and idempotency. GPU vs CPU latency table. README with production gaps |
| **Should** | React agent console. Quality gate with eDifFIQA(T) used as capture feedback. Passive PAD flag (MiniFASNet) routing to manual. Outbox table and poller to dcs-stub. Grafana dashboard. GitHub Actions CI with JaCoCo. Threshold sanity check on a small consenting-volunteer set |
| **Won't** | Real identity-authority or DCS integration, Kubernetes, Kafka, walk-through video tracking, certified PAD, demographic evaluation, HSM, multi-gate load testing, any real airline branding |
| 2-min demo | See §4.6 (the recommended idea's script) |
| Talking points | (1) **Gallery scoping is the biggest accuracy lever**: flight manifest, consented only, boarded removed, with the NIST FRTE and NISTIR 8381 evidence. (2) **Fail-safe default = manual.** Breakers make failure *fast* so queues keep moving, as EES and MUSE showed. (3) **Privacy by construction**: consent filter at query time, probes never persisted, no biometrics in logs, crypto-shredding. And honestly: an airline-held key is *not* EDPB scenario 2 |
| Likely follow-up questions | See §4.7 |
| Risks | Java learning curve (mitigated by the checkpoint and size budget). Windows GPU/CUDA set-up (onnxruntime-gpu 1.30 needs CUDA 13; 1.26 for CUDA 12) [V]. Non-commercial model weights (fine for a POC; say so). Demo faces need consent. Over-claiming accuracy |

### I2. Walk-through capture gate (I1 plus a live camera pipeline)

| Field | Content |
|---|---|
| Problems | P2, P4, P6, P16, plus I1's P13/P1/P14/P10 |
| What it shows | You know that **acquisition, not matching, dominates walk-through errors**: 97% of errors in the DHS 2022 rally [V] https://www.biometricupdate.com/202302/dhs-biometric-rally-results-show-groups-pose-little-challenge-to-effective-matching (23 Feb 2023) ⚠️OLD. You also know that quality should drive capture feedback, not rejection [V] https://www.biometricupdate.com/202609/better-face-recognition-exposes-limits-of-image-quality-scores (24 Sep 2026) |
| Stack | Java gate (as I1) + **Python capture client** (OpenCV webcam, SCRFD, ByteTrack, eDifFIQA best-shot, MiniFASNet) + Python model service |
| Architecture | I1 plus `capture-client` (Python): webcam → detect → track → per-track best-shot by quality → PAD → one POST per person. It flags more than one face in the gate zone |
| Blocks | SCRFD-10G, roboflow/trackers ByteTrack (Apache-2.0), eDifFIQA(T), MiniFASNet V2 (UniFace), YuNet fallback |
| Real vs stubbed | Real: webcam capture, tracking, quality, passive PAD. Stubbed: gate hardware, NIR/3D sensors, tailgating sensors, certified PAD |
| Must / Should / Won't | Must: I1 core + tracking + best-shot + multi-face flag. Should: PAD flag, quality feedback overlay. Won't: tailgating sensors, multi-camera fusion, PAD certification |
| 2-min demo | Walk toward the webcam with a track ID and quality score overlaid → BOARD. A second person steps in → MULTIPLE_FACES → manual. A phone photo held up → PAD result shown, whatever it is |
| Talking points | Acquisition dominates error; quality ≠ utility (DHS, UNHCR); PAD passes the lab but not necessarily the field (iBeta levels) |
| Follow-ups | "Would you run PAD at the gate or at enrolment?" Both: injection attacks target app enrolment (CEN/TS 18099), physical presentation attacks target the gate; certified PAD and NIR/3D come from vendors [V Phase 2]. "What if two passengers walk through together?" One decision per track; more than one face in the zone goes to manual; production uses mantrap or overhead sensing [I] |
| Risks | Webcam and lighting variability. PAD false results on camera. Much more ML plumbing. Weakest on feasibility |

### I3. Gate operations pack (I1 plus SLOs, chaos drills and safe matcher rollout)

| Field | Content |
|---|---|
| Problems | **P15, P14**, plus I1's set |
| What it shows | On-call readiness, covering rotational on-call within SLAs and logging and monitoring hooks: golden signals per gate, SLO alert rules, chaos switches, a runbook, and **safe matcher upgrade**. The upgrade uses template versioning, shadow scoring of the candidate model, and re-templating from reference images |
| Stack | Java gate + Python model service + Prometheus/Grafana (Docker) |
| Architecture | I1, plus Prometheus scraping gate and model services, Grafana dashboards, alert rules, and a second model in the model service (SFace as the "candidate") |
| Blocks | Actuator, Micrometer, `spring-boot-starter-opentelemetry` (optional), Resilience4j, Prometheus client (Python), R50 + SFace |
| Real vs stubbed | Real: metrics, alerts, dashboards, fault injection, shadow comparison. Stubbed: paging, Kubernetes, multi-site |
| Must / Should / Won't | Must: I1 core + dashboard + 2 alert rules (manual-fallback rate, p95 latency) + runbook. Should: shadow matcher and model-version guard. Won't: Kubernetes, PagerDuty, canary automation |
| 2-min demo | Dashboard shows a normal boarding. Inject mock-idp latency → breaker opens, the manual-fallback alert fires, then recovery. Enable the shadow model → agreement-rate panel |
| Talking points | FNIR is invisible in production, so monitor proxies: manual rate, score and margin drift, audited manual outcomes. Matchers change often: FRTE received 17 submissions in one cycle [V] https://www.biometricupdate.com/202608/nist-frte-11-shows-facial-verification-accuracy-converging (11 Aug 2026). Templates are version-bound, and reference images sit with the identity authority [I] |
| Follow-ups | "What SLOs would you propose?" p95 decision latency and a manual-fallback-rate budget per gate, with baselines agreed with ground ops [I]. "How would you roll out a new matcher?" Shadow first, then canary by gate, then re-template, then cut over, keeping rollback by version [I] |
| Risks | Largest scope. Dashboards eat time. Easy to over-build |

### I4. Flight-gallery threshold lab (offline evaluation)

| Field | Content |
|---|---|
| Problems | **P1**, P7, P8 |
| What it shows | Quantitative judgement: FNIR at fixed FPIR versus gallery size (300–600 vs larger), the effect of the top-2 margin rule on in-gallery false positives, and per-group differentials in the style of ISO/IEC 19795-10 [V https://www.iso.org/standard/81223.html] |
| Stack | **All Python** (scripts or notebook, GPU for embedding extraction) |
| Architecture | No services. Dataset → embeddings (GPU) → simulated flights with mated and non-mated probes → metrics and plots → short report |
| Blocks | ArcFace R50 / SFace / EdgeFace, FAISS, pyeer, BFW (research-only data) |
| Real vs stubbed | Real: models and error-rate maths. Stubbed: gate capture conditions (web photos ≠ gate captures), operational data |
| Must / Should / Won't | Must: FNIR-FPIR vs gallery size, margin-rule analysis. Should: per-group differentials. Won't: any claim about the airline's performance |
| 2-min demo | Three plots with one sentence each |
| Talking points | Set thresholds on FPIR over the flight gallery, not on FAR [V Phase 2]. The margin rule trades automation for safety. Differentials persist: West African and female subjects are the highest-disparity groups for most algorithms [V] https://www.biometricupdate.com/202609/nist-frte-11-update-shows-shifting-leaders-persistent-demographic-disparities (21 Sep 2026) |
| Follow-ups | "Would those numbers hold at DXB?" No: different images, population and matcher. The method transfers, not the numbers [I] |
| Risks | Dataset access and licences. Results mistaken for operational claims. Little target-stack overlap |

### I5. Passenger-held key match service (privacy deep-dive)

| Field | Content |
|---|---|
| Problems | **P10**, P12, P19 |
| What it shows | EDPB scenario 2 (central store, passenger-held key) versus 1:N walk-through. A CKKS-encrypted probe scored against a gallery. Crypto-shredding. The limits of template protection (inversion attacks [V] https://arxiv.org/abs/2602.13168, Feb 2026) |
| Stack | **All Python** (TenSEAL is Python; no Java CKKS library was verified) |
| Architecture | Wallet CLI (holds the secret key) → gate service → encrypted scoring service → decrypt only at the key holder |
| Blocks | TenSEAL 0.3.18, ArcFace R50 / SFace, a PolyProtect re-implementation |
| Real vs stubbed | Real: HE maths, key separation. Stubbed: wallet, HSM, key recovery |
| Must / Should / Won't | Must: encrypted 1:1 and small 1:N. Should: PolyProtect comparison. Won't: a production key-management system |
| 2-min demo | Enrol in the wallet, match encrypted at the gate, delete the key, show the template is useless |
| Talking points | Naive encrypted 1:N took 2.65 s for 500 templates on 2 vCPU [M], so there is an engineering gap to packing schemes like IDFace (126 ms for 1M) [V ICCV 2025]. Passenger-held keys push walk-through 1:N towards 1:1 with a token |
| Follow-ups | "Would this work with authority-held references?" Only if the identity provider participates in the key scheme, which is a governance question, not a code one [I] |
| Risks | Subtle crypto that is hard to defend line by line. Slow. Narrow target-stack fit |

---

## 3. Scoring

**Rubric (1–5), applied the same way to every idea:**

| Criterion (weight) | 5 | 3 | 1 |
|---|---|---|---|
| Relevance to the airline's problems (×3) | Directly addresses ≥3 of the Phase 1 top-5 | Mostly non-top-5 problems | Tangential |
| Discussion value (×3) | Leads naturally into their design, ops and privacy trade-offs and into the unknowns from Phases 1–2 | Interesting but narrow | Mostly a demo |
| Feasibility in 2 days, given your skills (×2) | Only your strongest stack; blocks already smoke-tested | A new language or framework for a *core* component (validated skeleton only) | Unlikely to finish |
| Target-stack fit (×1) | Core service in Java/Spring (JPA/Hibernate, Resilience4j, Actuator, JUnit) | Java only at the edges | No Java |
| Security and privacy depth (×1) | Consent, minimisation, encryption, retention, secure failure | Some | None |

**Matrix (each idea with its recommended stack; maximum 50):**

| Idea (stack) | Rel ×3 | Conv ×3 | Feas ×2 | Stack ×1 | Sec ×1 | **Total** | Defensibility line by line (not weighted) |
|---|---|---|---|---|---|---|---|
| **I1 Consent-first flight-gallery gate** (Java gate + Python model) | 5 | 5 | 3 | 5 | 4 | **45** | Medium–high, *if* the Java is kept small and plain (guardrails in §4.1) |
| I3 Gate operations pack (Java + Python + Prometheus/Grafana) | 5 | 5 | 2 | 5 | 4 | 43 | Medium |
| I2 Walk-through capture gate (Java gate + Python capture/model) | 5 | 4 | 2 | 5 | 4 | 40 | Medium–low (ML plumbing plus Java) |
| I5 Passenger-held key match service (all Python) | 4 | 4 | 3 | 1 | 5 | 36 | Low–medium (subtle crypto) |
| I4 Flight-gallery threshold lab (all Python) | 4 | 3 | 4 | 1 | 1 | 31 | High |

Arithmetic: I1 = 15+15+6+5+4 = 45; I3 = 15+15+4+5+4 = 43; I2 = 15+12+4+5+4 = 40; I5 = 12+12+6+1+5 = 36; I4 = 12+9+8+1+1 = 31.

**Stack sensitivity for I1 (same weights):**

| I1 stack option (Phase 3 `language_options`) | Rel | Conv | Feas | Stack | Sec | **Total** | Defensibility | Main risk |
|---|---|---|---|---|---|---|---|---|
| All Python (FastAPI gate + FastAPI model service) | 5 | 5 | **5** | **1** | 4 | **45** | High, your daily stack | Does not exercise the Java/Spring target stack |
| **Java/Spring Boot gate + Python model service** | 5 | 5 | 3 | **5** | 4 | **45** | Medium–high with guardrails | Learning Spring idioms (AOP, transactions, JPA) in the time |
| All Java (ONNX Runtime Java for models) | 5 | 5 | 2 | 5 | 4 | 43 | Low: SCRFD decoding, alignment and PAD cropping hand-ported | Time goes into ML plumbing instead of system design |

**Tie-break: why Java gate + Python model rather than all-Python.** Both options score 45. I pick Java + Python for four reasons [I]:
1. **Evidence beats claims.** Enterprise airline IT commonly runs Java, Spring Boot, Hibernate and microservices, with monitoring hooks and unit testing. A small Spring service you understand is concrete evidence of working beyond a daily Python stack.
2. **Prep synergy.** The hours spent learning Spring transactions, AOP proxies and Resilience4j for the gate service are the same hours you would spend preparing for Java questions anyway.
3. **A realistic boundary.** Phase 1 put the algorithm with the vendor and the thresholds and business rules with airline IT [I, Phase 1 §C]. A Python model service standing in for a "vendor SDK", plus a Java gate owning the business rules, mirrors that split. CompreFace uses the same Java-API-plus-Python-model split [V https://github.com/exadel-inc/CompreFace].
4. **The risk is capped** by a hard checkpoint at the end of Day 1 (§4.3). If the Java core is not green, the gate service is rebuilt in FastAPI behind the **same OpenAPI contract and the same test cases**, and the Java skeleton is kept as a documented spike. All-Python therefore stays a safe fallback, not a lost option.

---

## 4. Recommendation: I1 consent-first flight-gallery gate (+ one stretch goal)

### 4.1 Stack choice
**Choice:** Java 21 + Spring Boot 4.1.1 **gate-service**. Python 3.11 + FastAPI **model-service** (InsightFace 2.0: SCRFD-10G + ArcFace R50 on GPU, CPU switch; SFace/YuNet license-clean mode). Python FastAPI **mocks** (mock-idp standing in for the identity authority; dcs-stub). PostgreSQL. Optional React console.
- Spring Boot 4.1.1 supports Java 17–26. [V https://docs.spring.io/spring-boot/system-requirements.html]
- Phase 3 ran Boot 4.1.1 + `resilience4j-spring-boot4` 2.4.0 + Actuator/Prometheus end to end. [M]
- `onnxruntime-gpu` 1.30.0 wheels need CUDA 13; 1.26.0 is the CUDA 12 line. [V PyPI wheel metadata, Phase 3]

**Guardrails that keep it defensible:**
- Gate service ≤ ~800 lines of main code.
- Spring MVC (not WebFlux), Spring Data JPA, Flyway with one migration.
- Java records, no Lombok, no Spring Modulith, no Kafka.
- Constructor injection only. Every class listed in `docs/WALKTHROUGH.md` with **your own** explanation.
- *If you cannot explain a line, delete or simplify it.*

**The 2–3 sentence summary:**
> "I put the airline side (gallery scoping, consent, fallback, idempotency, encryption, metrics) in a small Spring Boot 4 service with JPA, Resilience4j and Micrometer, because that's your stack and that's where the design decisions live. The face models sit behind a stateless Python service, standing in for a vendor SDK, because that's where the model ecosystem and GPU support are. My production background is Python/FastAPI; I kept the Java deliberately plain so I can walk through every line, and building it was how I've been closing my Java gap."

*If the Day-1 checkpoint forces all-Python:*
> "I built both services in FastAPI because I'd rather show you a design I can defend line by line. The gate service maps one-to-one onto a Spring Boot microservice (a controller, a transactional service, JPA repositories, a Resilience4j breaker, Micrometer metrics), and I've written that mapping down and started a Spring spike."

**How the design maps to Spring, and how you close the gap (FastAPI → Spring):**

| You know (Python) | Spring equivalent in the gate service |
|---|---|
| FastAPI router + Pydantic models | `@RestController` + Java `record` DTOs + Bean Validation |
| Dependency injection via `Depends` | Constructor injection of `@Service` / `@Component` beans |
| SQLAlchemy session and unit of work | `@Transactional` service methods; Spring Data JPA (Hibernate) repositories |
| Alembic migrations | Flyway `V1__init.sql` |
| pybreaker + tenacity + httpx timeouts | Resilience4j `@CircuitBreaker(fallbackMethod=…)` + `RestClient` connect/read timeouts |
| prometheus_client | Micrometer `Counter` / `Timer` / `DistributionSummary` → `/actuator/prometheus` |
| APScheduler / cron | `@Scheduled` purge job |
| pytest + respx | JUnit 5 + Mockito + WireMock (or `MockRestServiceServer`); JaCoCo coverage |

**Java parts to be ready to walk through line by line:**
1. `DecisionPolicy`: a pure function covering thresholds, the top-2 margin, reason codes and its table-driven tests.
2. `GateDecisionService`: why external calls happen **outside** the `@Transactional` block; the conditional update `UPDATE … SET status='BOARDED' WHERE id=? AND status='ACTIVE'` and what 0 rows means; idempotency on `event_id`.
3. `IdentityClient` / `ModelClient`:
   - `RestClient` timeouts.
   - Resilience4j annotation semantics (Spring AOP proxy, so **self-invocation bypasses the breaker**; fallback method signature; OPEN, HALF_OPEN and CLOSED states).
   - Why there is no retry on the gate's hot path.
4. `TemplateCrypto`:
   - `AES/GCM/NoPadding`, 96-bit random IV, 128-bit tag.
   - AAD binding `flight|profile|model_id`.
   - Per-flight DEK wrapped by a KEK.
   - Why deleting the wrapped DEK is crypto-shredding.
   - What still leaks: plaintext templates in JVM memory during boarding.
5. `PurgeJob`, the `application.yml` resilience settings, the Micrometer metric names, and one WireMock fault test.

### 4.2 Architecture

```
[gate-sim CLI | webcam]                               [React agent console (should)]
        | POST /v1/gates/{gateId}/events                      | GET /v1/gates/{gateId}/events, /actuator/circuitbreakers
        | (image, flightId, Idempotency-Key)                   |
        v                                                      |
+------------------------- gate-service  (Java 21, Spring Boot 4.1.1) -------------------------------+
| GateController -> GateDecisionService -> DecisionPolicy (T, Δ, consent, boarded, reason codes)       |
|   ModelClient    --RestClient, timeout, @CircuitBreaker-->  model-service (FastAPI; InsightFace GPU|CPU)|
|   IdentityClient --RestClient, 300 ms, @CircuitBreaker-->   mock-idp  [gov identity stand-in, MOCK]     |
|   GalleryService: per-flight in-memory cache of decrypted templates (built T-60 or on demand)           |
|   TemplateCrypto: AES-256-GCM, per-flight DEK wrapped by KEK (local keystore = stand-in for KMS/HSM)    |
|   PurgeJob (@Scheduled): after departure delete rows + destroy DEK (crypto-shred)                        |
|   OutboxPoller (should) --> dcs-stub [MOCK]      Actuator: /health /prometheus /circuitbreakers          |
+---------------------------------------------|-------------------------------------------------------+
                                              v
        PostgreSQL: flight, passenger_consent, gallery_entry(ciphertext, iv, model_id), gate_event, outbox
```

**Service contracts** (the real identity-authority and DCS interfaces are not publicly known; these are hypothetical):

| Service | Endpoint | Purpose |
|---|---|---|
| model-service | `POST /v1/embed` (image) → `{model_id, faces:[{bbox, det_score, embedding[512], quality?, liveness?}], timings_ms}` | Stateless. Never sees identities. `DEVICE=cuda\|cpu`, `MODEL=r50\|sface` |
| mock-idp (identity-authority stand-in) | `GET /v1/profiles/{id}/reference-image`; `POST /v1/verify {profile_id, flight}` → `VALID\|INVALID`; `POST /admin/faults {latency_ms, error_rate, down}` | Identity anchor, 1:1 confirmation, fault injection |
| dcs-stub | `GET /v1/flights/{id}/manifest`; `POST /v1/boarding-events` | Manifest source and boarded-state sink |
| gate-service | `POST /v1/consents`, `DELETE /v1/consents/{id}`, `POST /v1/flights/{id}/gallery:build`, `POST /v1/gates/{gateId}/events`, `POST /v1/flights/{id}/depart` | Airline business logic |

**Gate-event sequence.** Any exception at any step returns `MANUAL` with a reason code. This is the fail-safe default.
1. If the Idempotency-Key has been seen, return the stored decision (no reprocessing).
2. The flight must be `BOARDING` with its gallery loaded; otherwise `MANUAL/GALLERY_UNAVAILABLE`.
3. `ModelClient.embed`: exactly one face, else `NO_FACE` or `MULTI_FACE`. Optionally apply `LOW_QUALITY` (as capture feedback) or `PAD_SUSPECT`.
4. `GalleryService.search(flight)`: candidates are `gallery_entry` rows with status `ACTIVE`, **joined with `passenger_consent` status `ACTIVE` at query time**, and with `model_id` equal to the probe's `model_id`. Take top-1 and top-2 by cosine.
5. `DecisionPolicy`: `top1 < T` → `NO_MATCH`; `top1 − top2 < Δ` → `AMBIGUOUS`.
6. `IdentityClient.confirm` (300 ms, breaker, no retry): timeout, error or open breaker → `IDP_UNAVAILABLE`; an `INVALID` answer → `IDP_REJECTED`.
7. In one transaction: conditional board update (0 rows → `ALREADY_BOARDED`), insert `gate_event`, insert `outbox`, commit, evict the entry from the cache.
8. Metrics and a structured log line: decision, reason, gate and score bucket. **No embeddings or images, and identity only when the decision is BOARD.**

**Failure modes (each one is a test):**

| Failure | Behaviour | Evidence |
|---|---|---|
| mock-idp slow (>300 ms) or down | `MANUAL/IDP_UNAVAILABLE`; after N failures the breaker opens and fails instantly; HALF_OPEN probes recover it | WireMock delay/500 tests; `/actuator/circuitbreakers`; demo |
| model-service down or slow | `MANUAL/MODEL_UNAVAILABLE` | Test + demo |
| mock-idp down during gallery build | Flight stays `GALLERY_UNAVAILABLE`, so the whole flight boards manually | Test |
| Duplicate gate event (retry) | Same decision returned, no double processing | Idempotency test |
| Two gates, same passenger | One `BOARD`, the other `ALREADY_BOARDED` | Concurrency test |
| Consent withdrawn mid-boarding | Entry removed immediately; next attempt `NO_MATCH` | Test + demo |
| Look-alikes or "twins" in the same flight | `AMBIGUOUS` → manual | Demo (the same volunteer enrolled under two profile IDs, labelled as a simulation) |
| Non-enrolled person at the gate | `NO_MATCH`; nothing identifying logged; probe discarded | Log inspection in the demo |
| DB down | Gate service returns 503; the gate simulator's client default is manual | Documented [I] |

**Privacy and crypto design (design choices, not facts about the airline):**
- Consent before matching (query-time join) and before logging (identity is written only for `BOARD`).
- Probes are processed in memory only.
- Reference images are fetched, embedded and discarded; only encrypted templates are stored.
- Per-flight DEK. `PurgeJob` deletes rows and the wrapped DEK after departure plus a grace period, so backups become unreadable. A test proves that decryption fails after the shred.
- **Honest limits:**
  - Decrypted templates live in JVM memory during boarding.
  - The KEK sits in a local keystore, not an HSM.
  - An airline-held key is **not** EDPB scenario 2.
  - Walk-through capture still *detects* people who have not consented; the POC minimises what happens next but does not prevent the capture. [I]
- CBP's partner-purge rule is the documented precedent for "don't retain". [V Federal Register 27 Oct 2025]

**Latency budget (targets [I], not measurements):**
- End-to-end decision p95 ≤ 1 s on the GPU desktop, split roughly as: embed ≤ 300 ms, gallery search ≤ 5 ms, identity confirm ≤ 300 ms (timeout), DB ≤ 20 ms.
- CPU reference from Phase 3 (2 vCPU): SCRFD-10G@640 ≈ 130 ms + R50 ≈ 138 ms per face; YuNet + SFace ≈ 47 ms; exact search over 500 templates ≈ 0.04 ms. [M]
- GPU numbers are still unmeasured and will come from the smoke tests and `tools/bench_latency.py`.

### 4.3 Two-day build plan (half-day blocks of about 4 hours)

| Block | Work | Exit criteria |
|---|---|---|
| **Pre-work (≤ 2 h, before Day 1)** | Run `phase3-smoke-tests.md` steps 0–3, 9, 10 and 11. Save as `docs/research/smoke-test-results.md`. Collect 4–6 **consenting** volunteers' photos (including yourself); no web or celebrity images | GPU line works (CUDA 13 or 12); Java 21 + Maven OK; GPU model and VRAM recorded |
| **D1-AM: Python foundations** | Repo skeleton; `docker compose` for Postgres. model-service `/v1/embed` (InsightFace R50, `DEVICE` and `MODEL` switches, `/metrics`) + pytest. mocks: mock-idp (reference images, `/verify`, `/admin/faults`) + dcs-stub (manifest). Seed script: one flight, volunteers, a "twin" pair | `curl` returns one 512-d embedding; a mock-idp latency switch visibly delays `/verify` |
| **D1-PM: Java gate core (CHECKPOINT)** | Spring Boot 4.1.1 project (webmvc, data-jpa, flyway, postgresql, actuator, aspectj, resilience4j-spring-boot4, micrometer-prometheus; test: JUnit 5, Mockito, WireMock). Flyway V1. Write **DecisionPolicy tests first**, then the policy. `TemplateCrypto` + tests. Gallery build (idp → embed → encrypt → store). Gate-event happy path | **17:30 checkpoint.** DecisionPolicy and TemplateCrypto tests green **and** one end-to-end `BOARD` from gate-sim → continue in Java. Otherwise → rebuild the gate in FastAPI with the same contract and tests; move Java to `spikes/` |
| **D2-AM: resilience, consent, retention** | `RestClient` timeouts + `@CircuitBreaker` fallbacks → MANUAL. WireMock tests (delay, 500, down). Idempotency-Key. Conditional board update and concurrency test. Consent withdraw. Purge + crypto-shred + test. Micrometer counters, timers and summaries | Every row of the failure-mode table has a passing test. JaCoCo report generated. `/actuator/prometheus` shows `gate_decisions_total` |
| **D2-PM: evidence and story** | `tools/bench_latency.py` (end-to-end and per component for GPU, CPU, and CPU capped at 4 threads) → README table. `tools/gate_sim.py` demo scenarios. README (with **production gaps**), `docs/architecture.md`, three ADRs, `docs/WALKTHROUGH.md` (**written by you**). Record the 2-min video. Then, in order, only if time remains: React console → quality/PAD flags → outbox poller → CI → **stretch** | Recording done; README complete; you can explain every Java class aloud without notes |

**Drop order if behind:** stretch → CI → outbox → quality/PAD → React console (use Actuator JSON and logs in the recording instead). **Never drop:** fallback tests, the crypto-shred test, the production-gaps section.

**Optional stretch goal: safe matcher upgrade (from I3).**
- Every `gallery_entry` carries a `model_id`, and a `ModelVersionGuard` refuses cross-model comparisons (unit test).
- A **shadow gallery** is built with the candidate model (SFace) from mock-idp reference images (re-templating).
- On each event, the candidate model scores asynchronously and **never affects the decision**; the result goes to `gate_shadow_agreement_total{agree}`.
- A per-flight cut-over flag completes the upgrade.
- Talking point: re-templating needs the reference images. In this design they sit with the identity provider, so who does the re-extraction is a governance and SLA question. [I]

### 4.4 Repository structure (`docs/research/` unchanged)
```
POC/
├── CLAUDE.md                      # build brief for Claude Code
├── README.md                      # outline in §4.5
├── docs/
│   ├── research/                  # phase1–4 (unchanged) + smoke-test-results.md
│   ├── architecture.md            # diagram, sequence, failure-mode table, latency budget
│   ├── decisions/                 # ADR-0001 stack, 0002 matching location, 0003 template encryption & retention, 0004 fallback policy
│   ├── WALKTHROUGH.md             # your line-by-line notes on the Java classes
│   └── demo-script.md
├── gate-service/                  # Java 21, Spring Boot 4.1.1, Maven
│   ├── pom.xml
│   └── src/
│       ├── main/java/poc/gate/{api,decision,gallery,crypto,clients,events,metrics,config}/
│       ├── main/resources/{application.yml, db/migration/V1__init.sql}
│       └── test/java/poc/gate/...
├── model-service/                 # Python 3.11, FastAPI, InsightFace 2.0 / OpenCV
│   ├── app/{main.py, models.py, schemas.py, settings.py}
│   ├── tests/
│   └── requirements.txt
├── mocks/                         # Python FastAPI: mock_idp (identity-authority stand-in) + dcs_stub + fault injection
├── gate-console/                  # React + Vite (should)
├── tools/                         # gate_sim.py, bench_latency.py, seed.py
├── ops/                           # docker-compose.yml, prometheus.yml, grafana/ (should)
├── data/                          # .gitignored: volunteer photos + consent note; KEK keystore
└── spikes/                        # only if the Day-1 checkpoint triggers
```

### 4.5 README outline
1. **What this is**, with a banner: *"A 2-day personal prototype. Not production-ready. Not affiliated with any airline or government agency; all external systems are mocks."*
2. **The problem it illustrates**: P13, P1, P14, P10 (link to `docs/research/phase1-problem.md`).
3. **Architecture**: diagram; what is real and what is mocked; service contracts.
4. **Gate decision policy**: sequence, reason codes, how T and Δ were set (illustrative, calibrated only on volunteer photos).
5. **Privacy and security**: consent enforcement points, data minimisation, what is logged, AES-GCM envelope and per-flight DEK, crypto-shredding, EDPB scenarios comparison, honest limits.
6. **Resilience and fallback**: the failure-mode table and matching tests; breaker settings and why there is no retry on the hot path.
7. **Observability**: metric names, example PromQL, proposed SLOs (targets, not agreements).
8. **Latency: GPU vs CPU**. Columns: component (embed, search, identity confirm, end-to-end) × GPU / CPU (all cores) / CPU capped at 4 threads (an edge proxy); p50 and p95; hardware, driver, ORT and CUDA versions; method (`tools/bench_latency.py`, N=200, warm-up 20). Also list the Phase 3 cloud CPU reference numbers for comparison.
9. **Running it**: prerequisites; GPU/CPU switches; `docker compose up`; seed; demo scenarios.
10. **Tests and coverage**: what's covered, JaCoCo and pytest reports, and the gap to 100% unit tests in CI.
11. **Models and licences**: InsightFace weights are non-commercial research only; `MODEL=sface` + YuNet is the license-clean mode.
12. **Production gaps** (explicit, not exhaustive):
    - No certified matcher (NIST-evaluated vendor SDK) and no certified PAD (iBeta ISO/IEC 30107-3, NIR/3D).
    - Thresholds are not calibrated at a target FPIR on operational data; no demographic evaluation.
    - No real identity-authority or DCS contract, no mTLS or message signing, no audit or legal review (PDPL/GDPR).
    - KEK not in an HSM/KMS, no key rotation, plaintext templates in memory.
    - Single instance: no HA/DR, no Kubernetes, no load test at an A380 boarding peak.
    - No multi-camera walk-through or tailgating control.
    - No accessibility handling (children, PRM, face coverings); no agreed SLOs or runbooks; no threat model or pentest.
13. **What I'd ask and do next**: the open questions in §4.9 and the stretch goal.
14. **How AI tools were used**: Claude Code for research and scaffolding; every line reviewed, and correctness validated with tests and smoke tests (AI-assisted development with responsible validation).
15. **References**: `docs/research/`.

### 4.6 Two-minute demo script (a recording, not a live demo)
| Time | Show | Say |
|---|---|---|
| 0:00–0:15 | Architecture diagram | "Airline gate service, a stateless model service standing in for a vendor, and a mock identity provider standing in for the identity authority." |
| 0:15–0:40 | Open boarding for flight POC-001; gallery built for 4 consented volunteers; volunteer A at the gate → `BOARD` (score and margin shown); gallery size 4 → 3 | "We match only against this flight's consented gallery; once boarded, you're out of it." |
| 0:40–0:55 | A tries again → `NO_MATCH`; a non-enrolled person → `NO_MATCH`, and the log shows no identity | "Re-boarding is impossible, and strangers leave no trace beyond a counter." |
| 0:55–1:10 | Withdraw B's consent → B at the gate → `NO_MATCH` | "Consent is checked at query time, not only at enrolment." |
| 1:10–1:35 | Inject 2 s latency into mock-idp → `IDP_UNAVAILABLE` → breaker `OPEN` (instant manual) → clear the fault → HALF_OPEN → CLOSED | "Any failure means manual boarding, fast, so the queue keeps moving." |
| 1:35–1:45 | The "twin" pair (same volunteer under two profiles, labelled as a simulation) → `AMBIGUOUS` | "Close top-two scores go to a human, not a guess." |
| 1:45–2:00 | Depart the flight → purge → attempt to decrypt a template fails; closing line on the README's production-gaps section | "Templates are crypto-shredded after departure. And this is a toy; here's what production needs." |

### 4.7 Talking points and likely follow-up questions

**Three talking points:**
1. **Gallery scoping is the biggest lever.** Median NIST FRTE FNIR rises about 17× from 640k to 12M identities [V 16 Sep 2026], and NIST's boarding simulation uses about 420-person galleries [V NISTIR 8381 ⚠️OLD]. A manifest gallery with boarded exclusion and a top-2 margin is airline business logic, not vendor magic. [I]
2. **Fail-safe means fast manual.** A breaker turns a slow dependency into an instant, visible manual decision. EES suspensions and the MUSE outage show why the degraded mode must be designed and rehearsed. [V 12 Aug 2026; 21 Sep 2025]
3. **Privacy is structural.** Consent is checked at query time; probes are never stored; there are no biometrics in logs; templates are crypto-shredded. The limits are also clear: an airline-held key is not EDPB scenario 2, and walk-through cameras still see people who have not consented. [V EDPB 2024 ⚠️OLD; I]

**Follow-up questions with strong answers:**

| Question | Strong answer |
|---|---|
| "Why match in the airline service? CBP's TVS matches at the government side." | "Both are valid. TVS builds manifest galleries and matches in CBP's cloud, and airline partners must purge images [V Federal Register 2025]. I put the matcher behind an interface, so the gate logic doesn't care where matching runs. The trade-offs are latency, availability during an identity-authority outage, and data minimisation. Where does the airline run it today?" |
| "How did you pick the threshold and margin?" | "They're illustrative and checked on a handful of volunteer photos, so nothing to rely on. In production you set T at a target FPIR over flight-size galleries using the vendor's calibration and operational data, and watch the manual-fallback rate per gate. The margin rule deliberately trades automation for safety with families and twins [V NIST IR 8439 ⚠️OLD]." |
| "What happens if the identity authority is down during an A380 boarding?" | "The breaker opens and every gate goes to manual within one timeout, not per passenger. Whether you allow local-only matching with later reconciliation is a security and business policy decision, not a coding one. I'd want to know what the airline's policy is." |
| "How would you know it's working in production?" | "FNIR isn't observable live, so I'd track proxies: manual-fallback rate and reason mix per gate, score and margin distributions for drift after camera changes, p95 latency, breaker state, and audited outcomes of manual checks as ground truth." |
| "How do you upgrade the matcher?" | "Templates are model-version bound, so run the new model in shadow first, measure agreement, re-template from reference images, canary by gate, then cut over, keeping the old version for rollback. The open question is who holds the reference images and runs re-extraction." |
| "Are the templates actually safe?" | "At rest, yes, reasonably: AES-GCM, a per-flight key, and crypto-shredding. In use, no: plaintext sits in memory during boarding, and embeddings can be inverted into usable faces [V arXiv 2602.13168, Feb 2026]. HE schemes exist [V IDFace, ICCV 2025], but my naive CKKS test was about 2.6 s for 500 templates [M], so it isn't a gate-latency tool yet." |
| "You used AI to build this?" | "Yes, for research and scaffolding. I reviewed every line, wrote the walkthrough myself, and relied on tests and measured smoke tests rather than generated claims. That's what responsible AI-assisted development means to me." |

### 4.8 Ninety-second verbal pitch
> "The airline runs face recognition at DXB check-in, lounges and some gates, with identity anchored in identity-authority profiles. What interested me was the part an airline IT team actually owns: not the matcher, but everything around it. So I built a small boarding-gate POC. A mock identity provider stands in for the identity authority. The gate service, in Spring Boot, builds a gallery of only that flight's consented passengers and matches 1:N against it, and it removes you once you've boarded. Scoping the gallery like that is the single biggest accuracy lever in NIST's data. If the top two scores are too close, which is the twins-and-families case, it sends you to a human. If anything fails, including the identity provider being slow, a circuit breaker turns that into an immediate manual-boarding decision so the queue keeps moving. Consent is enforced at query time, nothing biometric goes into logs, and templates are encrypted with a per-flight key that's destroyed after departure. The models run in a separate Python service on GPU or CPU, and I measured both, because gates run on modest hardware. Everything is instrumented, so you can see the fallback rate and breaker state. It's a toy: mocks instead of real integrations, uncalibrated thresholds, no certified liveness. But it gave me a very concrete set of questions about how your team handles exactly these trade-offs."

### 4.9 Five questions the POC leads into
1. "Where does 1:N matching run for boarding today: on the gate device, in an airline service, or at the identity authority? Does the gate receive a decision, or scores you threshold yourselves?" (Not publicly known.)
2. "When the identity-authority link degrades mid-boarding, what is the policy: manual for everyone, or local matching with reconciliation? Who owns that decision, and how is it rehearsed?"
3. "How is the boarding gallery scoped (manifest only, boarded exclusion, transfer passengers), and how are thresholds or margins tuned and monitored per gate?"
4. "When the matcher or vendor model is upgraded, who re-extracts templates from reference images, and how do you canary it across 200+ cameras?"
5. "What is retained after departure (images, templates, logs), and how do you handle consent withdrawal and EU passengers given the EDPB guidance?"

### 4.10 Risks and mitigations
| Risk | Mitigation |
|---|---|
| Java learning curve eats Day 1 | Tests first on the pure `DecisionPolicy`; 17:30 checkpoint with the FastAPI fallback; size guardrails |
| CUDA mismatch on Windows | Smoke-test step 2 before Day 1; 1.26.0/CUDA 12 fallback; CPU mode is always valid for the demo |
| Docker / GPU on Windows | Run the model service natively in a venv; only Postgres (and optionally Grafana) in Docker |
| Non-commercial weights | State it in the README; `MODEL=sface` license-clean mode |
| Demo data and consent | Only consenting volunteers; photos kept out of git; the "twin" labelled as a simulation |
| Over-claiming | Production-gaps section; "illustrative thresholds"; no accuracy claims; latency reported with the hardware |
| Looking like real airline or government software | No logos or branding; everything named `poc`, `mock-idp`, `dcs-stub` |

---

## 5. Sources (primary facts used in this plan)
- Airline biometrics investment and identity-authority anchoring: https://www.emirates.com/media-centre/speedy-seamless-secure--emirates-invests-in-innovative-facial-recognition-technology-across-dubai-international-dxb/ (3 Nov 2025)
- Airline biometrics page ("may automatically recognize you"): https://www.emirates.com/us/english/experience/contactless-journey/biometrics/ (Oct 2025)
- Red Carpet and Smart Gates, Jan–Aug 2026: https://gulfnews.com/uae/dubais-biometric-red-carpet-processes-745188-travellers-in-eight-months-of-2026-1.500666738 (8 Sep 2026)
- NIST FRTE 1:N gallery-size effect: https://www.biometricupdate.com/202609/new-nist-frte-1n-results-illustrate-facial-recognition-accuracys-multidimensionality (16 Sep 2026)
- NIST FRTE 1:1 convergence and submissions: https://www.biometricupdate.com/202608/nist-frte-11-shows-facial-verification-accuracy-converging (11 Aug 2026)
- NIST FRTE 1:1 demographic disparities: https://www.biometricupdate.com/202609/nist-frte-11-update-shows-shifting-leaders-persistent-demographic-disparities (21 Sep 2026)
- NISTIR 8381 paperless travel: https://nvlpubs.nist.gov/nistpubs/ir/2021/NIST.IR.8381.pdf (Jul 2021) ⚠️OLD
- CBP TVS final rule: https://www.federalregister.gov/documents/2025/10/27/2025-19655/collection-of-biometric-data-from-aliens-upon-entry-to-and-departure-from-the-united-states (27 Oct 2025)
- EDPB Opinion 11/2024: https://www.edpb.europa.eu/system/files/documents/2024-05/edpb_opinion_202411_facialrecognitionairports_en.pdf (23 May 2024) ⚠️OLD, operative
- DHS Rally 2022 (acquisition errors): https://www.biometricupdate.com/202302/dhs-biometric-rally-results-show-groups-pose-little-challenge-to-effective-matching (23 Feb 2023) ⚠️OLD
- Quality scores and utility: https://www.biometricupdate.com/202609/better-face-recognition-exposes-limits-of-image-quality-scores (24 Sep 2026)
- EES degraded mode: https://time.com/article/2026/08/12/eu-europe-travel-entry-exit-system-ees-passport-biometric/ (12 Aug 2026)
- Collins MUSE outage: https://www.abc.net.au/news/2025-09-21/cyber-attack-collins-aerospace-disrupts-europe-airports/105798264 (21 Sep 2025)
- Template inversion: https://arxiv.org/abs/2602.13168 (Feb 2026)
- IDFace HE search (126 ms / 1M templates): https://openaccess.thecvf.com/content/ICCV2025/html/Kim_IDFace_Face_Template_Protection_for_Efficient_and_Secure_Identification_ICCV_2025_paper.html (ICCV 2025)
- NIST twins study: https://nvlpubs.nist.gov/nistpubs/ir/2022/NIST.IR.8439.pdf (2022) ⚠️OLD
- Spring Boot 4.1.1 requirements: https://docs.spring.io/spring-boot/system-requirements.html (accessed 30 Sep 2026)
- InsightFace licence (non-commercial weights): https://github.com/deepinsight/insightface (accessed 30 Sep 2026)
- CompreFace (Java API + Python model service precedent): https://github.com/exadel-inc/CompreFace (last commit 2023-11-14)
- ISO/IEC 19795-10: https://www.iso.org/standard/81223.html (2024)
- Phase 3 measurements [M] and package checks: `docs/research/phase3-opensource.md`, `docs/research/phase3-smoke-tests.md`

