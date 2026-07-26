-- proctor schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `proctor` database only.

CREATE TABLE proctor_sessions (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    session_public_id UUID        NOT NULL,
    proctor_public_id UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    opened_at         TIMESTAMPTZ NOT NULL,
    closed_at         TIMESTAMPTZ,
    last_sequence_no  INTEGER     NOT NULL DEFAULT 0,
    last_hash         VARCHAR(64)
);
CREATE INDEX idx_proctor_sessions_session_proctor ON proctor_sessions (session_public_id, proctor_public_id);
CREATE INDEX idx_proctor_sessions_tenant ON proctor_sessions (tenant_id);

CREATE TABLE violation_events (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID        NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    proctor_session_id BIGINT      NOT NULL REFERENCES proctor_sessions (id) ON DELETE CASCADE,
    session_public_id  UUID        NOT NULL,
    attempt_public_id  UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    violation_type     VARCHAR(30) NOT NULL,
    detail             TEXT,
    sequence_no        INTEGER     NOT NULL,
    prev_hash          VARCHAR(64),
    hash               VARCHAR(64) NOT NULL,
    detected_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_violation_session_seq UNIQUE (proctor_session_id, sequence_no)
);
CREATE INDEX idx_violation_events_session ON violation_events (proctor_session_id);
CREATE INDEX idx_violation_events_exam_session ON violation_events (session_public_id);
CREATE INDEX idx_violation_events_attempt ON violation_events (attempt_public_id);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
