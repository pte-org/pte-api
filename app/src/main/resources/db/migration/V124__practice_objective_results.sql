ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS objective_score INTEGER,
    ADD COLUMN IF NOT EXISTS objective_evaluated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS objective_evaluator_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective_result_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective_evidence_identity_hash VARCHAR(64);

ALTER TABLE practice_explanations
    ADD COLUMN IF NOT EXISTS objective_score INTEGER,
    ADD COLUMN IF NOT EXISTS objective_evaluator_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective_result_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS objective_evidence_identity_hash VARCHAR(64);
