-- AI question generation: durable jobs (lease-based worker), one row per requested item, and provenance on questions.
-- Additive only: rollback is itembank.generation.enabled=false; AI-made DRAFT questions stay valid without the feature.
CREATE TABLE question_generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by_user_public_id UUID NOT NULL,
    task_type_key VARCHAR(64) NOT NULL,
    cefr_level VARCHAR(2) NOT NULL,
    pool VARCHAR(16) NOT NULL,
    topic_hint VARCHAR(200),
    requested_count INT NOT NULL CHECK (requested_count BETWEEN 1 AND 10),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    succeeded_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    worker_id VARCHAR(64),
    lease_until TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(64)
);
CREATE INDEX ix_qgen_jobs_status_created ON question_generation_jobs (status, created_at);
CREATE INDEX ix_qgen_jobs_requester_created ON question_generation_jobs (requested_by_user_public_id, created_at DESC);
-- One active job per requester, enforced by the database so two concurrent submits cannot both pass a check-then-insert.
CREATE UNIQUE INDEX uk_qgen_jobs_one_active_per_requester
    ON question_generation_jobs (requested_by_user_public_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING');

CREATE TABLE question_generation_job_items (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES question_generation_jobs (id) ON DELETE CASCADE,
    item_index INT NOT NULL,
    stage VARCHAR(24) NOT NULL
        CHECK (stage IN ('QUEUED', 'GENERATING', 'VALIDATING', 'REVIEWING', 'RENDERING_MEDIA', 'SAVED', 'FAILED',
                         'CANCELLED')),
    question_public_id UUID,
    failure_code VARCHAR(64),
    failure_note VARCHAR(300),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_qgen_job_items_job_index UNIQUE (job_id, item_index)
);

ALTER TABLE questions ADD COLUMN generation_meta JSONB;
ALTER TABLE questions ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE questions ADD COLUMN generation_job_public_id UUID;
ALTER TABLE questions ADD COLUMN generation_item_index INT;
-- Not unique: hand-written questions and revisions may legitimately repeat content; the generator enforces uniqueness.
CREATE INDEX ix_questions_task_type_content_hash ON questions (task_type_key, content_hash);
-- Idempotency: a retried item can never create a second question for the same job slot.
CREATE UNIQUE INDEX uk_questions_generation_slot
    ON questions (generation_job_public_id, generation_item_index)
    WHERE generation_job_public_id IS NOT NULL;
COMMENT ON COLUMN questions.generation_meta IS 'AI provenance and review sheet; null for hand-written questions';
COMMENT ON COLUMN questions.content_hash IS 'SHA-256 of the normalized stimulus text; set for AI-generated questions only';
