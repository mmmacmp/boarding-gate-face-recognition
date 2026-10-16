# ADR-0004: Fail-safe = fast manual boarding

- Status: accepted (30 Sep 2026).
- Decision: any exception, timeout or open circuit breaker yields `MANUAL` with a reason code, never an error that
  blocks the gate. Hot-path calls (model embed, identity verify) have hard timeouts (800 ms / 300 ms) and a
  Resilience4j breaker (window 10, min 5 calls, 50% failure or slow-call rate, 10 s open, 2 half-open calls).
  **No retry on the hot path.** Database down → HTTP 503 with a `MANUAL / DB_UNAVAILABLE` body; the simulator treats
  any non-200 as manual. Duplicate requests are idempotent (unique `Idempotency-Key`), and boarding is a conditional
  update so a passenger boards at most once.
- Why: a breaker turns a slow dependency into an immediate, visible manual decision so the queue keeps moving
  (degraded modes such as the EES suspensions and the MUSE outage show this must be designed and rehearsed).
- Consequences: availability of automation depends on the identity provider; whether local-only matching with later
  reconciliation is allowed during an outage is a policy decision, **not publicly known** (an open question for a real operator).
