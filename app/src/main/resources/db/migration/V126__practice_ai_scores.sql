ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS ai_score INTEGER,
    ADD COLUMN IF NOT EXISTS ai_sub_scores_json TEXT,
    ADD COLUMN IF NOT EXISTS ai_provider VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ai_model VARCHAR(160),
    ADD COLUMN IF NOT EXISTS ai_provider_version VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ai_transcript TEXT,
    ADD COLUMN IF NOT EXISTS ai_detail_json TEXT,
    ADD COLUMN IF NOT EXISTS ai_scored_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ai_result_hash VARCHAR(64);
