-- Revision-level product availability. The legacy pool remains during the
-- rolling deployment and is used only to seed the independent flags.
ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS available_for_practice BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS available_for_exam BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS availability_flags_explicit BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE questions
SET available_for_practice = CASE WHEN pool = 'PRACTICE' THEN TRUE ELSE FALSE END,
    available_for_exam = CASE WHEN pool = 'EXAM' THEN TRUE ELSE FALSE END,
    availability_flags_explicit = TRUE
WHERE availability_flags_explicit = FALSE;

CREATE INDEX IF NOT EXISTS idx_questions_practice_channel
    ON questions (available_for_practice, visibility, status, is_current, task_type_key)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_questions_exam_channel
    ON questions (available_for_exam, visibility, status, is_current, task_type_key)
    WHERE deleted = FALSE;
