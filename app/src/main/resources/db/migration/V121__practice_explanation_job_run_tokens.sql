ALTER TABLE practice_explanation_jobs
    ADD COLUMN IF NOT EXISTS run_token UUID;

UPDATE practice_explanation_jobs
SET run_token = public_id
WHERE run_token IS NULL;

ALTER TABLE practice_explanation_jobs
    ALTER COLUMN run_token SET NOT NULL;