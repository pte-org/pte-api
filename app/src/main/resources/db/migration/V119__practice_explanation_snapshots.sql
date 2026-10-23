CREATE TABLE IF NOT EXISTS practice_explanations (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    practice_session_item_id BIGINT NOT NULL REFERENCES practice_session_items(id),
    student_public_id UUID NOT NULL,
    language VARCHAR(5) NOT NULL,
    generation_sequence INTEGER NOT NULL DEFAULT 0,
    submission_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary TEXT,
    strengths_json TEXT,
    improvements_json TEXT,
    rubric_feedback_json TEXT,
    rubric_version VARCHAR(64) NOT NULL,
    requested_model VARCHAR(128),
    actual_model_version VARCHAR(128),
    provider_version VARCHAR(128),
    generated_at TIMESTAMPTZ,
    CONSTRAINT uk_practice_explanation_slot UNIQUE (practice_session_item_id, language, generation_sequence)
);

CREATE INDEX IF NOT EXISTS idx_practice_explanation_item
    ON practice_explanations (practice_session_item_id, language);
