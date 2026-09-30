# ADR-0001: Java gate-service + Python model-service

- Status: accepted (30 Sep 2026). Day-1 checkpoint passed, so no switch to all-Python.
- Context: the role's stack is Java/Spring; the face-model ecosystem and GPU support are in Python. Both options
  scored 45/50 in the Phase 4 matrix.
- Decision: the airline business logic (consent, gallery, decision, fallback, encryption, metrics) is a small Spring
  Boot 4.1.1 service; the models sit behind a stateless FastAPI service, a stand-in for a vendor SDK.
- Guardrails: Spring MVC (no WebFlux), constructor injection, records, no Lombok, ~900 code lines (902 measured,
  excluding blank/comment/import lines), every class listed in `docs/WALKTHROUGH.md`.
- Checkpoint (17:30 Day 1): `DecisionPolicy` + `TemplateCrypto` tests green and one end-to-end BOARD via
  `tools/gate_sim.py` → passed; otherwise the gate-service would have been rebuilt in FastAPI behind the same contract.
- Consequences: two services and one extra network hop (needs timeouts and a breaker); realistic vendor/airline split.
  Built with JDK 25, targets Java 21; Maven via the wrapper.
