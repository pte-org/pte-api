-- scoring schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `scoring` database only.

CREATE TABLE scoring_answers (
    id                   BIGSERIAL PRIMARY KEY,
    public_id            UUID        NOT NULL UNIQUE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    answer_public_id     UUID        NOT NULL UNIQUE,
    attempt_public_id    UUID        NOT NULL,
    pinned_item_public_id UUID       NOT NULL,
    session_public_id    UUID        NOT NULL,
    tenant_id            UUID        NOT NULL,
    task_type            VARCHAR(60) NOT NULL,
    payload              TEXT,
    correct_answer_text  TEXT,
    options_json         TEXT,
    expired              BOOLEAN     NOT NULL DEFAULT FALSE,
    status               VARCHAR(20) NOT NULL,
    raw_score            INTEGER,
    scored_at            TIMESTAMPTZ
);
CREATE INDEX idx_scoring_answers_session ON scoring_answers (session_public_id);
CREATE INDEX idx_scoring_answers_attempt ON scoring_answers (attempt_public_id);
CREATE INDEX idx_scoring_answers_status ON scoring_answers (status);

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
