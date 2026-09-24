ALTER TABLE exam_sessions
    ADD COLUMN IF NOT EXISTS max_retries_per_student INTEGER NOT NULL DEFAULT 0;

ALTER TABLE exam_sessions
    ADD CONSTRAINT chk_exam_sessions_max_retries_per_student
        CHECK (max_retries_per_student BETWEEN 0 AND 9);

CREATE TABLE IF NOT EXISTS exam_session_selected_skills (
    session_id BIGINT NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    skill VARCHAR(16) NOT NULL,
    CONSTRAINT uk_exam_session_selected_skills_session_skill UNIQUE (session_id, skill),
    CONSTRAINT chk_exam_session_selected_skill
        CHECK (skill IN ('SPEAKING', 'WRITING', 'READING', 'LISTENING'))
);

-- Existing drafts with a pinned template resolve to its full section set; do not
-- infer scope for already-generated sessions or sessions missing that reference.
INSERT INTO exam_session_selected_skills (session_id, skill)
SELECT DISTINCT s.id, UPPER(TRIM(item.section))
FROM exam_sessions s
JOIN score_templates template
    ON template.public_id = s.template_public_id
   AND template.version = s.template_version
JOIN score_template_items item ON item.template_id = template.id
WHERE s.status = 'DRAFT'
  AND s.template_public_id IS NOT NULL
  AND UPPER(TRIM(item.section)) IN ('SPEAKING', 'WRITING', 'READING', 'LISTENING')
ON CONFLICT (session_id, skill) DO NOTHING;

ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS attempt_number INTEGER NOT NULL DEFAULT 1;

ALTER TABLE exam_attempts
    ADD CONSTRAINT chk_exam_attempts_attempt_number_positive CHECK (attempt_number > 0);

ALTER TABLE exam_attempts
    DROP CONSTRAINT IF EXISTS exam_attempts_session_public_id_student_public_id_key;

ALTER TABLE exam_attempts
    ADD CONSTRAINT uk_exam_attempt_session_student_number
        UNIQUE (session_public_id, student_public_id, attempt_number);
