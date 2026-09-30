# ADR-0003: Template encryption and retention

- Status: accepted (30 Sep 2026).
- Decision:
  - AES-256-GCM per template, 96-bit random IV, 128-bit tag, AAD = `flight|profile|model`.
  - One DEK per flight, stored only wrapped (AES key wrap) by a KEK from a local PKCS12 keystore (KMS/HSM stand-in).
  - **No plaintext cache** between events: the DEK is unwrapped and the matchable rows decrypted per gate event
    (measured 2.6 ms p50 / 5.7 ms p95 for 49 entries on the GPU desktop, including the DB read).
  - After departure + grace, `PurgeJob` deletes the rows and the wrapped DEK in one transaction (crypto-shred) and
    writes an audit row; a gallery rebuild keeps `BOARDED` rows and the existing DEK, and a departed flight cannot be
    rebuilt.
- Alternatives rejected: an in-memory plaintext cache per flight (more code: eviction after commit; plaintext for the
  whole boarding); pgvector plaintext search (no encryption at rest for templates); homomorphic search (2.65 s for
  500 templates in Phase 3).
- Limits (documented as production gaps): plaintext in JVM memory during an event; KEK in a file; the wrapped DEK
  shares the database and its backups with the ciphertext, so pre-purge backups stay decryptable with the KEK; no
  key rotation; the consent-withdrawal audit row has no subject reference.
