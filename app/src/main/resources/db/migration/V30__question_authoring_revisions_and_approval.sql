ALTER TABLE questions ADD COLUMN IF NOT EXISTS revision_group_public_id UUID;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS revision_number INTEGER;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS supersedes_public_id UUID;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS is_current BOOLEAN;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

UPDATE questions
SET revision_group_public_id = public_id,
    revision_number = 1,
    is_current = TRUE,
    version = 0
WHERE revision_group_public_id IS NULL;

ALTER TABLE questions ALTER COLUMN revision_group_public_id SET NOT NULL;
ALTER TABLE questions ALTER COLUMN revision_number SET NOT NULL;
ALTER TABLE questions ALTER COLUMN is_current SET NOT NULL;
ALTER TABLE questions ALTER COLUMN version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_current_revision_group
    ON questions (revision_group_public_id)
    WHERE is_current = TRUE AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_questions_revision_group
    ON questions (revision_group_public_id, revision_number);
