ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS form_word_count INTEGER,
    ADD COLUMN IF NOT EXISTS form_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS form_evaluator_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS form_result_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS form_evidence_identity_hash VARCHAR(64);

ALTER TABLE practice_explanations
    ADD COLUMN IF NOT EXISTS form_word_count INTEGER,
    ADD COLUMN IF NOT EXISTS form_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS form_evaluator_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS form_result_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS form_evidence_identity_hash VARCHAR(64);
