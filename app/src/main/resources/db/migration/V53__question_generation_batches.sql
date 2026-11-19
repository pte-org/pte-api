-- Batch AI question generation: one batch per author request, split into child jobs of at most 10 items that run
-- one after another. Additive except the job status CHECK and the one-active-job index, which are recreated.
CREATE TABLE question_generation_batches (
    id                          BIGSERIAL PRIMARY KEY,
    public_id                   UUID        NOT NULL UNIQUE,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    deleted                     BOOLEAN     NOT NULL DEFAULT FALSE,
    requested_by_user_public_id UUID        NOT NULL,
    pool                        VARCHAR(16) NOT NULL,
    -- [{taskType, cefrLevel, count, topicHints[], imageKind}]
    rows                        JSONB       NOT NULL,
    total_requested             INT         NOT NULL CHECK (total_requested BETWEEN 1 AND 500),
    status                      VARCHAR(24) NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED')),
    succeeded_count             INT         NOT NULL DEFAULT 0,
    failed_count                INT         NOT NULL DEFAULT 0,
    cancelled_count             INT         NOT NULL DEFAULT 0,
    cancel_requested            BOOLEAN     NOT NULL DEFAULT FALSE,
    failure_code                VARCHAR(64),
    started_at                  TIMESTAMPTZ,
    finished_at                 TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_qgen_batches_one_open_per_requester ON question_generation_batches (requested_by_user_public_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING');
CREATE INDEX ix_qgen_batches_requester_created ON question_generation_batches (requested_by_user_public_id, created_at DESC);
CREATE INDEX ix_qgen_batches_open ON question_generation_batches (status)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING');

ALTER TABLE question_generation_jobs
    ADD COLUMN batch_id BIGINT REFERENCES question_generation_batches (id),
    ADD COLUMN batch_seq INT,
    ADD COLUMN batch_row_index INT,
    ADD CONSTRAINT ck_qgen_jobs_batch_fields CHECK ((batch_id IS NULL) = (batch_seq IS NULL)
                                                AND (batch_id IS NULL) = (batch_row_index IS NULL));
CREATE UNIQUE INDEX uk_qgen_jobs_batch_seq ON question_generation_jobs (batch_id, batch_seq) WHERE batch_id IS NOT NULL;
CREATE INDEX ix_qgen_jobs_batch ON question_generation_jobs (batch_id) WHERE batch_id IS NOT NULL;
-- Claim order: standalone and preview jobs first, then batch children FIFO by batch creation and run order.
CREATE INDEX ix_qgen_jobs_queued ON question_generation_jobs ((batch_id IS NOT NULL), created_at, batch_seq, id)
    WHERE status = 'QUEUED' AND deleted = false;

-- The V34 status CHECK is inline (generated name), so it is found by its definition, not by name. Only the status
-- CHECK of this table mentions CANCELLING.
DO $$
DECLARE c text;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'question_generation_jobs'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%status%' AND pg_get_constraintdef(oid) LIKE '%CANCELLING%'
    LOOP
        EXECUTE format('ALTER TABLE question_generation_jobs DROP CONSTRAINT %I', c);
    END LOOP;
END $$;
ALTER TABLE question_generation_jobs ADD CONSTRAINT ck_qgen_jobs_status
    CHECK (status IN ('PENDING', 'QUEUED', 'RUNNING', 'CANCELLING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- Preview jobs (save_questions = false, V40) no longer count for the one-active-job rule, so the question form can
-- preview while a batch of the same author runs. PENDING batch children stay outside the index.
DROP INDEX uk_qgen_jobs_one_active_per_requester;
CREATE UNIQUE INDEX uk_qgen_jobs_one_active_per_requester ON question_generation_jobs (requested_by_user_public_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING') AND save_questions;

INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_max_items_per_batch', '50', 'Questions one generation batch may ask for'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_max_items_per_batch');
