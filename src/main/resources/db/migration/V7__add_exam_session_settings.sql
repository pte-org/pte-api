-- Exam session settings: skill subset, proctor requirement, availability window, retry count.
ALTER TABLE exam ADD COLUMN IF NOT EXISTS skill_subset_reading BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS skill_subset_listening BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS skill_subset_writing BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS skill_subset_speaking BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS proctor_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS availability_start TIMESTAMPTZ;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS availability_end TIMESTAMPTZ;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS max_retry_count INTEGER NOT NULL DEFAULT 0;

-- Per-section (skill + part) time-limit overrides. No JSON blob: each override is its own
-- row with a real FK, so Phase 8's extend-time action has a well-typed target to increment.
CREATE TABLE IF NOT EXISTS session_time_override (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL REFERENCES exam(id) ON DELETE CASCADE,
    skill VARCHAR(20) NOT NULL,
    part INTEGER NOT NULL,
    override_minutes INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_time_override_exam_skill_part UNIQUE (exam_id, skill, part)
);
