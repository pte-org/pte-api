ALTER TABLE practice_explanations
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS can_retry BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS practice_explanation_jobs (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    explanation_id BIGINT NOT NULL REFERENCES practice_explanations(id),
    student_public_id UUID NOT NULL,
    language VARCHAR(5) NOT NULL,
    generation_sequence INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    last_failure_code VARCHAR(64),
    idempotency_key VARCHAR(128),
    CONSTRAINT uk_practice_explanation_job_explanation UNIQUE (explanation_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_practice_explanation_job_idempotency
    ON practice_explanation_jobs (student_public_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_practice_explanation_job_due
    ON practice_explanation_jobs (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_practice_explanation_job_lease
    ON practice_explanation_jobs (status, lease_until);

CREATE TABLE IF NOT EXISTS practice_explanation_outbox (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    job_id BIGINT NOT NULL REFERENCES practice_explanation_jobs(id),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_failure_code VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_practice_explanation_outbox_due
    ON practice_explanation_outbox (published_at, next_attempt_at);
