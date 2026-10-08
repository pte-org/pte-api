-- Academic workflow ownership. Legacy rows remain ownerless and are guarded
-- by the PLATFORM_ADMIN override; audit history is not used as ownership.
ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS author_user_public_id UUID;

ALTER TABLE question_types
    ADD COLUMN IF NOT EXISTS author_user_public_id UUID;

ALTER TABLE score_templates
    ADD COLUMN IF NOT EXISTS author_user_public_id UUID;

ALTER TABLE exam_blueprints
    ADD COLUMN IF NOT EXISTS author_user_public_id UUID;

CREATE INDEX IF NOT EXISTS idx_questions_academic_author
    ON questions (author_user_public_id, status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_question_types_academic_author
    ON question_types (author_user_public_id, lifecycle_status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_score_templates_academic_author
    ON score_templates (author_user_public_id, status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_exam_blueprints_academic_author
    ON exam_blueprints (author_user_public_id, status)
    WHERE deleted = FALSE;
