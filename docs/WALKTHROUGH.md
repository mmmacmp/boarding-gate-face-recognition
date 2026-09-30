# gate-service walkthrough (written by the user)

Goal: explain every Java class aloud, line by line, without notes. Claude lists the classes and the concepts to
cover after each build block; **the explanations below are yours, in your own words**. If you can't explain a
line, delete or simplify it.

## Step 3: gate core (Day-1 checkpoint)

### decision/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `ReasonCode` | Java `enum`; why every MANUAL decision carries a reason | |
| `Candidate` | Java `record` (immutable data class, like a frozen dataclass) | |
| `Decision` | record with static factory methods; nested enum; why MANUAL never carries `profileId` | |
| `DecisionPolicy` | pure function, no Spring; per-model thresholds; `top1 < T` vs `top1 - top2 < Δ`; one-entry gallery; streams + `Comparator` | |

### crypto/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `TemplateCrypto` | AES-256-GCM, 96-bit random IV, 128-bit tag, AAD = `flight\|profile\|model`; DEK vs KEK; AES key wrap; why deleting the wrapped DEK is crypto-shredding; `float[]` ↔ bytes; checked vs unchecked exceptions | |

### config/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `GateProperties` | `@ConfigurationProperties` binding YAML into records (like Pydantic Settings); `Duration` parsing; bracketed map keys | |
| `GateConfig` | `@Configuration` + `@Bean` methods; why `DecisionPolicy`/`TemplateCrypto` stay plain Java; loading a KEK from a PKCS12 keystore | |
| `GateServiceApplication` | `@SpringBootApplication` (component scan, auto-configuration); `@ConfigurationPropertiesScan` | |

### clients/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `RestClients` | `RestClient` (≈ `httpx.Client`); connect vs read timeout; why each client gets its own `Builder` | |
| `ModelClient` | POST raw bytes; JSON → nested records (snake_case via global Jackson setting); `RestClientException` covers timeouts and 4xx/5xx | |
| `IdentityClient` | the mock identity provider; why no retry on the hot path | |
| `DcsClient` | manifest → records with `Instant` | |
| `DependencyUnavailableException` | unchecked exception carrying a `ReasonCode` | |

### gallery/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `Flight`, `PassengerConsent`, `GalleryEntry` | `@Entity`, `@Id`, `@GeneratedValue(IDENTITY)`, `@Enumerated(STRING)`; protected no-arg constructor for JPA; camelCase → snake_case columns | |
| `FlightRepository`, `PassengerConsentRepository` | Spring Data interfaces: implementation generated at startup | |
| `GalleryEntryRepository` | native `@Query`; consent join at query time; `@Modifying`; conditional `UPDATE ... WHERE status='ACTIVE'` and what 0 rows means | |
| `GalleryService` | constructor injection; build flow; why `build` is NOT `@Transactional`; exact top-2 search; dot product = cosine for unit vectors; no plaintext cache | |

### events/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `GateEvent`, `GateEventRepository` | what is stored per decision (and what is not) | |
| `BoardingWriter` | `@Transactional` via a Spring AOP proxy; why a separate bean (self-invocation bypasses the proxy); commit/rollback; ALREADY_BOARDED | |
| `GateDecisionService` | the gate-event sequence; fail-safe `try/catch` → MANUAL; external calls before the transaction; structured log with identity only for BOARD | |

### api/
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `GateController` | `@RestController`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestPart MultipartFile` (≈ FastAPI `File`/`Form`/`Header`) | |
| `FlightController` | `ResponseEntity` for status codes; 503 when the gallery can't be built | |
| `ConsentController` | `@RequestBody` record; `@ResponseStatus(CREATED)`; `save` = insert or update | |

### Resources
| File | Concepts to cover | Your explanation |
|---|---|---|
| `application.yml` | profiles of timeouts; `open-in-view: false`; `ddl-auto: validate`; `config.import` for local secrets | |
| `db/migration/V1__init.sql` | Flyway (≈ Alembic) runs once at startup; tables and why `profile_id` is nullable in `gate_event` | |

## Step 4: resilience, consent, retention, metrics

### Changed classes
| Class | New concepts to cover | Your explanation |
|---|---|---|
| `ModelClient`, `IdentityClient` | `@CircuitBreaker(name, fallbackMethod)`; the AOP proxy (why self-invocation would bypass it); fallback signature = same params + `Throwable`; `CallNotPermittedException` when OPEN; CLOSED → OPEN → HALF_OPEN → CLOSED; why no retry on the hot path; why `referenceImage` has no breaker | |
| `GateDecisionService` | Idempotency-Key lookup; the race on the same key (unique constraint + `DataIntegrityViolationException` → return the winner); where metrics are recorded (not for replays) | |
| `GalleryService` | rebuild preserves boarded state (delete ACTIVE only, skip BOARDED, keep the DEK); `depart`; `@Transactional purge` and why `PurgeJob` calls it through another bean; dirty checking in `flight.shred()` | |
| `Flight` | lifecycle BUILDING → BOARDING → DEPARTED → PURGED; `shred()` = crypto-shredding | |
| `GalleryEntryRepository` | `deleteActiveByFlight`, `findBoardedProfileIds`, `deleteActiveByProfile`; `@Transactional` on repository methods | |
| `FlightRepository` | derived query from a method name (`findByStatusAndDepartedAtBefore`) | |
| `FlightController`, `ConsentController` | `POST /depart`; 409 for a closed flight; `DELETE /v1/consents/{id}`; `ResponseEntity.of(Optional)` → 200 or 404 | |
| `GateServiceApplication` | `@EnableScheduling` | |

### New classes
| Class | Concepts to cover | Your explanation |
|---|---|---|
| `ConsentService` | `@Transactional` withdraw; managed entities and dirty checking (no `save()`); why BOARDED rows are kept | |
| `PurgeJob` | `@Scheduled(fixedDelayString=...)` (≈ APScheduler interval job); grace period; one transaction per flight | |
| `AuditLog`, `AuditLogRepository` | append-only retention audit, no biometrics | |
| `GateMetrics` | Micrometer `Counter`/`Timer`/`DistributionSummary`; dotted names → Prometheus `_total`/`_seconds`; low-cardinality tags (never a profile id) | |
| `ApiExceptionHandler` | `@RestControllerAdvice` + `@ExceptionHandler` (≈ FastAPI exception handlers); why DB down is a 503 with a MANUAL body | |

### Config and tests
| Item | Concepts to cover | Your explanation |
|---|---|---|
| `application.yml` `resilience4j.circuitbreaker` | sliding window 10, minimum 5 calls, 50% failure/slow rate, slow-call threshold, 10 s open, 2 half-open calls | |
| `pom.xml` | Failsafe (`*IT`, `mvn verify`) vs Surefire (`*Test`, `mvn test`); JaCoCo agent + report | |
| `AbstractGateIT` | `@SpringBootTest`; `@DynamicPropertySource`; WireMock as a fake HTTP server; unique ids instead of DB cleanup | |
| `BoardingIT`, `ResilienceIT`, `RetentionIT` | one test per failure-mode row; the two-gate race with a latch; `OutputCaptureExtension` for "no identity in logs" | |
| `ApiExceptionHandlerTest` | `@WebMvcTest` (web layer only) + `@MockitoBean` | |

## Step 5: small Java changes

| Item | Concepts to cover | Your explanation |
|---|---|---|
| `@JsonNaming(SnakeCaseStrategy)` on each JSON record | Jackson 3 (`tools.jackson`); why per record instead of the global `spring.jackson.property-naming-strategy` (the global one also renamed actuator JSON) | |
| `GateMetrics.recordSearch` + `gate.gallery.search` timer | cost of "no plaintext cache" (unwrap DEK + DB read + decrypt + dot products); `publishPercentiles(0.5, 0.95)` | |
| `application.yml` R50 threshold 0.44 | where it came from (`tools/score_analysis.py`), why it is still illustrative, the FNMR it costs | |
| `pom.xml` Mockito `-javaagent` | `maven-dependency-plugin:properties`; `@{argLine}` keeps the JaCoCo agent | |

## Step 6: demo console support

### Java
| Item | Concepts to cover | Your explanation |
|---|---|---|
| `StageTimings` | a mutable data holder with public fields (why not a record here); `@JsonNaming`; never stored | |
| `GateEvent.timings` | `@Transient` = not a column; only present in the response of the request that made the decision | |
| `GateDecisionService` stage timing | `try/finally` so a failed or timed-out stage still reports its time; `LOW_QUALITY` = only faces smaller than `MIN_FACE_PX` (a size proxy, not an image-quality model) | |
| `GateController` `GET /v1/events`, `GET /v1/policy` | derived query `findTop20ByOrderByIdDesc`; returning a config record as JSON | |
| `application.yml` `circuitbreakerevents` | Resilience4j's event buffer; why the console reads transitions from it instead of polling state | |

### Console (React, for the walkthrough of the demo tool)
| File | Concepts to cover | Your explanation |
|---|---|---|
| `vite.config.js` | dev-server proxy (why no CORS is needed); the dev-only `/demo/probe` allowlist and `/demo/reset` | |
| `usePoll.js` | `useEffect` + cleanup (like cancelling an asyncio task); why loaders live outside components | |
| `GatePanel.jsx`, `Webcam.jsx` | component state with `useState`; object URLs; `getUserMedia` + canvas capture; synthetic images never rendered | |
| `DecisionBanner.jsx`, `reasons.js` | re-mount with `key` to replay the CSS animation; the one privacy rule `shownProfile` | |
| `OpsPanel.jsx`, `BreakerTimeline.jsx`, `LatencyWaterfall.jsx`, `RecentEvents.jsx` | turning transition events into timeline segments; waterfall offsets | |
