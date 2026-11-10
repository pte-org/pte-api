-- A job started from the question form writes its question into the form only: the person decides whether to save
-- it. Such a job has save_questions = false, and its item ends READY (written, reviewed, media made, not saved).
ALTER TABLE question_generation_jobs
    ADD COLUMN save_questions BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE question_generation_job_items DROP CONSTRAINT IF EXISTS question_generation_job_items_stage_check;
ALTER TABLE question_generation_job_items ADD CONSTRAINT question_generation_job_items_stage_check
    CHECK (stage IN ('QUEUED', 'GENERATING', 'VALIDATING', 'REVIEWING', 'RENDERING_MEDIA', 'READY', 'SAVED', 'FAILED',
                     'CANCELLED'));
