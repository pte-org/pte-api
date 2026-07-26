-- authoring schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `authoring` database only.

CREATE TABLE questions (
    id                   BIGSERIAL PRIMARY KEY,
    public_id            UUID        NOT NULL UNIQUE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    pte_task_type        VARCHAR(60)  NOT NULL,
    visibility           VARCHAR(20)  NOT NULL,
    tenant_id            UUID,
    status               VARCHAR(20)  NOT NULL,
    title                VARCHAR(300) NOT NULL,
    prompt_text          TEXT,
    audio_prompt_ref     UUID,
    image_prompt_ref     UUID,
    reference_answer_text TEXT,
    correct_answer_text  TEXT,
    min_word_count       INTEGER,
    max_word_count       INTEGER
);
CREATE INDEX idx_questions_tenant ON questions (tenant_id);
CREATE INDEX idx_questions_visibility ON questions (visibility);
CREATE INDEX idx_questions_task_type ON questions (pte_task_type);

CREATE TABLE question_options (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    question_id BIGINT      NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    text        TEXT        NOT NULL,
    correct     BOOLEAN     NOT NULL,
    order_index INTEGER     NOT NULL
);
CREATE INDEX idx_options_question ON question_options (question_id);

CREATE TABLE exam_blueprints (
    id         BIGSERIAL PRIMARY KEY,
    public_id  UUID        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    name       VARCHAR(300) NOT NULL,
    tenant_id  UUID,
    status     VARCHAR(20) NOT NULL
);
CREATE INDEX idx_blueprints_tenant ON exam_blueprints (tenant_id);

CREATE TABLE blueprint_items (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID        NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    blueprint_id       BIGINT      NOT NULL REFERENCES exam_blueprints (id) ON DELETE CASCADE,
    question_public_id UUID        NOT NULL,
    section            VARCHAR(20) NOT NULL,
    order_index        INTEGER     NOT NULL
);
CREATE INDEX idx_blueprint_items_blueprint ON blueprint_items (blueprint_id);

CREATE TABLE exam_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    public_id                   UUID        NOT NULL UNIQUE,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    deleted                     BOOLEAN     NOT NULL DEFAULT FALSE,
    name                        VARCHAR(300) NOT NULL,
    version                     INTEGER     NOT NULL,
    source_blueprint_public_id  UUID        NOT NULL,
    tenant_id                   UUID
);
CREATE INDEX idx_snapshots_tenant ON exam_snapshots (tenant_id);

CREATE TABLE snapshot_items (
    id                        BIGSERIAL PRIMARY KEY,
    public_id                 UUID        NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    snapshot_id               BIGINT      NOT NULL REFERENCES exam_snapshots (id) ON DELETE CASCADE,
    source_question_public_id UUID        NOT NULL,
    pte_task_type             VARCHAR(60) NOT NULL,
    section                   VARCHAR(20) NOT NULL,
    order_index               INTEGER     NOT NULL,
    title                     VARCHAR(300) NOT NULL,
    prompt_text               TEXT,
    audio_prompt_ref          UUID,
    image_prompt_ref          UUID,
    reference_answer_text     TEXT,
    correct_answer_text       TEXT,
    min_word_count            INTEGER,
    max_word_count            INTEGER,
    options_json              TEXT
);
CREATE INDEX idx_snapshot_items_snapshot ON snapshot_items (snapshot_id);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
