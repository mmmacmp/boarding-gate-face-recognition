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
