ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS max_retries_per_student INTEGER NOT NULL DEFAULT 0;

ALTER TABLE exam_attempts
    ADD CONSTRAINT chk_exam_attempts_retry_limit CHECK (max_retries_per_student BETWEEN 0 AND 9);
