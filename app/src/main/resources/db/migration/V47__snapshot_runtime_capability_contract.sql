-- Phase 4: freeze the delivery/runtime contract with the exam snapshot.
-- All new columns are nullable so already-published snapshots remain readable;
-- the application enforces completeness for snapshots published after this
-- migration. No historical snapshot content is rewritten here.

ALTER TABLE snapshot_items
    ADD COLUMN IF NOT EXISTS task_type_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_profile_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_behavior_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_renderer_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_answer_schema_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_required_client_capabilities VARCHAR(512),
    ADD COLUMN IF NOT EXISTS runtime_profile_status VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_mapping_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS runtime_mapping_status VARCHAR(32);

ALTER TABLE pinned_items
    ADD COLUMN IF NOT EXISTS task_type_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_profile_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_behavior_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_renderer_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_answer_schema_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_required_client_capabilities VARCHAR(512),
    ADD COLUMN IF NOT EXISTS runtime_profile_status VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_mapping_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS runtime_mapping_status VARCHAR(32);

ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS capability_fingerprint VARCHAR(2048);
