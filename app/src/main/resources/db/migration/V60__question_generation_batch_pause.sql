-- A generation batch can be paused and resumed. PAUSING (its running children are being stopped) and PAUSED are open
-- statuses: a paused batch still holds its requester's one open batch. A child being stopped is PAUSING until its
-- worker hands it back to PENDING. paused_at drives the automatic cancel of a batch paused too long. Additive.

ALTER TABLE question_generation_batches ADD COLUMN paused_at TIMESTAMPTZ NULL;

-- The V53 status CHECKs are inline (generated names), so they are found by their definition, not by name.
DO $$
DECLARE c text;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'question_generation_batches'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%PARTIALLY_COMPLETED%'
    LOOP
        EXECUTE format('ALTER TABLE question_generation_batches DROP CONSTRAINT %I', c);
    END LOOP;
END $$;
ALTER TABLE question_generation_batches ADD CONSTRAINT ck_qgen_batches_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'PAUSING', 'PAUSED', 'COMPLETED', 'PARTIALLY_COMPLETED',
                      'FAILED', 'CANCELLED'));

DROP INDEX IF EXISTS uk_qgen_batches_one_open_per_requester;
CREATE UNIQUE INDEX uk_qgen_batches_one_open_per_requester ON question_generation_batches (requested_by_user_public_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'PAUSING', 'PAUSED');

DROP INDEX IF EXISTS ix_qgen_batches_open;
CREATE INDEX ix_qgen_batches_open ON question_generation_batches (status)
    WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'PAUSING', 'PAUSED');

CREATE INDEX ix_qgen_batches_paused_at ON question_generation_batches (paused_at) WHERE status = 'PAUSED';

ALTER TABLE question_generation_jobs DROP CONSTRAINT IF EXISTS ck_qgen_jobs_status;
ALTER TABLE question_generation_jobs ADD CONSTRAINT ck_qgen_jobs_status
    CHECK (status IN ('PENDING', 'QUEUED', 'RUNNING', 'CANCELLING', 'PAUSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- The expiry is a platform setting a platform admin edits beside the other generation limits. It is seeded like them,
-- because reading a missing setting inside a transaction marks that transaction for rollback.
INSERT INTO platform_settings (public_id, created_at, updated_at, deleted, setting_key, value, description)
SELECT gen_random_uuid(), now(), now(), false, 'question_generation_paused_batch_expiry_hours', '24',
       'Hours a paused generation batch is kept before it is cancelled (0 keeps it); applies to batches already paused'
WHERE NOT EXISTS (SELECT 1 FROM platform_settings WHERE setting_key = 'question_generation_paused_batch_expiry_hours');
