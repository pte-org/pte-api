-- Immutable runtime/scoring provenance for newly published snapshots and
-- pinned attempts. V51 already owns task_type_key and nullable legacy enum
-- columns; this migration adds only the remaining contract fields.
ALTER TABLE snapshot_items
    ADD COLUMN IF NOT EXISTS task_type_display_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS runtime_screen_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_mode VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_min_supported_app_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_implementation_version VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_scoring_configuration_digest VARCHAR(128);

ALTER TABLE pinned_items
    ADD COLUMN IF NOT EXISTS task_type_display_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS runtime_screen_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_mode VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_min_supported_app_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_implementation_version VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_scoring_configuration_digest VARCHAR(128);

-- Historical rows remain readable. New rows are validated by the publish/pin
-- services before they are persisted, so an incomplete custom contract can
-- never enter a newly published snapshot.
UPDATE snapshot_items
   SET task_type_display_name = COALESCE(task_type_display_name, task_type_key, task_type_code),
       runtime_screen_key = COALESCE(runtime_screen_key, runtime_renderer_key),
       runtime_contract_version = COALESCE(runtime_contract_version, runtime_profile_version),
       runtime_scoring_mode = COALESCE(runtime_scoring_mode,
           CASE WHEN runtime_scoring_profile_key = 'UNSCORED' THEN 'NONE' ELSE 'SCORED' END),
       runtime_scoring_implementation_version = COALESCE(runtime_scoring_implementation_version,
           runtime_scoring_profile_key || ':v' || runtime_scoring_profile_version)
 WHERE runtime_profile_key IS NOT NULL;

UPDATE pinned_items
   SET task_type_display_name = COALESCE(task_type_display_name, task_type_key, task_type_code),
       runtime_screen_key = COALESCE(runtime_screen_key, runtime_renderer_key),
       runtime_contract_version = COALESCE(runtime_contract_version, runtime_profile_version),
       runtime_scoring_mode = COALESCE(runtime_scoring_mode,
           CASE WHEN runtime_scoring_profile_key = 'UNSCORED' THEN 'NONE' ELSE 'SCORED' END),
       runtime_scoring_implementation_version = COALESCE(runtime_scoring_implementation_version,
           runtime_scoring_profile_key || ':v' || runtime_scoring_profile_version)
 WHERE runtime_profile_key IS NOT NULL;
