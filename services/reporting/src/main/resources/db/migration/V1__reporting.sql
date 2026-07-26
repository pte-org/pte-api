-- reporting schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `reporting` database only.

CREATE TABLE attempt_reports (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID        NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    attempt_public_id  UUID        NOT NULL UNIQUE,
    session_public_id  UUID        NOT NULL,
    student_public_id  UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    published          BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at       TIMESTAMPTZ
);
CREATE INDEX idx_attempt_reports_session ON attempt_reports (session_public_id);

CREATE TABLE answer_projections (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    answer_public_id  UUID        NOT NULL UNIQUE,
    attempt_public_id UUID        NOT NULL,
    task_type         VARCHAR(60) NOT NULL,
    raw_score         INTEGER,
    scored            BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_answer_projections_attempt ON answer_projections (attempt_public_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
