-- The whole POC schema in one Flyway migration. Hibernate only validates against it (ddl-auto: validate).

CREATE TABLE flight (
    flight_id      varchar(16) PRIMARY KEY,
    status         varchar(16) NOT NULL,          -- BUILDING | BOARDING | DEPARTED | PURGED
    departure_time timestamptz NOT NULL,
    model_id       varchar(32),                   -- model the gallery was built with; probes must match it
    wrapped_dek    bytea,                         -- per-flight data key, wrapped by the KEK; NULL = crypto-shredded
    departed_at    timestamptz
);

-- Consent is per passenger profile and is checked at query time, not only at enrolment.
CREATE TABLE passenger_consent (
    profile_id varchar(32) PRIMARY KEY,
    status     varchar(16) NOT NULL,              -- ACTIVE | WITHDRAWN
    updated_at timestamptz NOT NULL
);

-- Encrypted templates only: no images, no plaintext embeddings.
CREATE TABLE gallery_entry (
    id         bigserial PRIMARY KEY,
    flight_id  varchar(16) NOT NULL REFERENCES flight (flight_id),
    profile_id varchar(32) NOT NULL,
    model_id   varchar(32) NOT NULL,
    iv         bytea NOT NULL,                    -- 12 bytes, random per template
    ciphertext bytea NOT NULL,                    -- AES-256-GCM(template) || 16-byte tag; AAD = flight|profile|model
    status     varchar(16) NOT NULL,              -- ACTIVE | BOARDED
    created_at timestamptz NOT NULL,
    UNIQUE (flight_id, profile_id, model_id)
);
CREATE INDEX gallery_entry_flight_idx ON gallery_entry (flight_id, model_id, status);

-- One row per gate decision. profile_id is only written for BOARD (identity is logged only when boarding).
CREATE TABLE gate_event (
    id              bigserial PRIMARY KEY,
    idempotency_key varchar(64) NOT NULL UNIQUE,  -- a retried request returns the stored decision
    gate_id         varchar(16) NOT NULL,
    flight_id       varchar(16) NOT NULL,
    outcome         varchar(8)  NOT NULL,         -- BOARD | MANUAL
    reason          varchar(32) NOT NULL,
    profile_id      varchar(32),
    top1            double precision,
    top2            double precision,
    created_at      timestamptz NOT NULL
);

-- (should) Boarding events for the DCS, written in the same transaction as the board update.
CREATE TABLE outbox (
    id         bigserial PRIMARY KEY,
    event_type varchar(32) NOT NULL,
    payload    text NOT NULL,
    created_at timestamptz NOT NULL,
    sent_at    timestamptz
);

-- Retention actions (purge / crypto-shred), for audit.
CREATE TABLE audit_log (
    id         bigserial PRIMARY KEY,
    action     varchar(32) NOT NULL,
    flight_id  varchar(16),
    detail     text,
    created_at timestamptz NOT NULL
);
