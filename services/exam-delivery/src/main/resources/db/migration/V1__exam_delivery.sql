-- exam_delivery schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `exam_delivery` database only.

CREATE TABLE exam_attempts (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID        NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    session_public_id  UUID        NOT NULL,
    student_public_id  UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    status             VARCHAR(20) NOT NULL,
    started_at         TIMESTAMPTZ,
    submitted_at       TIMESTAMPTZ,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_attempt_session_student UNIQUE (session_public_id, student_public_id)
);
CREATE INDEX idx_attempts_tenant ON exam_attempts (tenant_id);

CREATE TABLE pinned_exam_snapshots (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID        NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    attempt_id                BIGINT      NOT NULL UNIQUE REFERENCES exam_attempts (id) ON DELETE CASCADE,
    source_snapshot_public_id UUID        NOT NULL,
    source_session_public_id  UUID        NOT NULL,
    tenant_id                 UUID        NOT NULL
);

CREATE TABLE pinned_items (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID        NOT NULL UNIQUE,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    deleted                BOOLEAN     NOT NULL DEFAULT FALSE,
    pinned_snapshot_id     BIGINT      NOT NULL REFERENCES pinned_exam_snapshots (id) ON DELETE CASCADE,
    order_index            INTEGER     NOT NULL,
    section                VARCHAR(20) NOT NULL,
    task_type              VARCHAR(60) NOT NULL,
    title                  VARCHAR(300) NOT NULL,
    prompt_text            TEXT,
    audio_prompt_ref       UUID,
    image_prompt_ref       UUID,
    reference_answer_text  TEXT,
    correct_answer_text    TEXT,
    min_word_count         INTEGER,
    max_word_count         INTEGER,
    options_json           TEXT,
    prep_seconds           INTEGER     NOT NULL,
    response_seconds       INTEGER     NOT NULL
);
CREATE INDEX idx_pinned_items_snapshot ON pinned_items (pinned_snapshot_id);

CREATE TABLE timer_states (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID        NOT NULL UNIQUE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    attempt_id           BIGINT      NOT NULL UNIQUE REFERENCES exam_attempts (id) ON DELETE CASCADE,
    current_order_index  INTEGER     NOT NULL,
    phase                VARCHAR(20) NOT NULL,
    task_started_at      TIMESTAMPTZ NOT NULL,
    prep_deadline        TIMESTAMPTZ NOT NULL,
    response_deadline    TIMESTAMPTZ NOT NULL
);

CREATE TABLE attempt_answers (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    attempt_id       BIGINT      NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    pinned_item_id   BIGINT      NOT NULL REFERENCES pinned_items (id) ON DELETE CASCADE,
    payload          TEXT,
    status           VARCHAR(20) NOT NULL,
    expired          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_answer_attempt_item UNIQUE (attempt_id, pinned_item_id)
);
CREATE INDEX idx_answers_attempt ON attempt_answers (attempt_id);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
