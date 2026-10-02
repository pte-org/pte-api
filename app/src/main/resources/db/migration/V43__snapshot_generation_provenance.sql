ALTER TABLE exam_snapshots
    ADD COLUMN IF NOT EXISTS generation_algorithm_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS generation_seed BIGINT,
    ADD COLUMN IF NOT EXISTS pool_policy_fingerprint VARCHAR(128);
