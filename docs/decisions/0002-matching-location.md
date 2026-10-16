# ADR-0002: 1:N matching in the airline gate-service, against a per-flight gallery

- Status: accepted for the POC (30 Sep 2026).
- Context: where boarding 1:N matching runs in the real system (gate device, airline service, or identity provider)
  is **not publicly known**. CBP's TVS matches on the government side against manifest galleries; airline partners
  must purge images.
- Decision: match in the gate-service against only the flight's consented, `ACTIVE` gallery (exact cosine search over
  decrypted templates), then confirm 1:1 at the identity provider. The model-service only produces embeddings.
- Why: gallery size is the biggest accuracy lever; scoping, consent, boarded exclusion and the margin rule are
  airline business logic, independent of where the matcher runs. The matcher sits behind an interface.
- Consequences: templates exist on the airline side (encrypted at rest, plaintext per event in memory); an
  identity-provider outage becomes a manual-boarding decision; this is **not** an EDPB passenger-held-key design.
- Open question for a real deployment: where does matching run today, and does the gate get a decision or scores?
