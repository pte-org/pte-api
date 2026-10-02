ALTER TABLE score_templates
    ADD COLUMN IF NOT EXISTS template_policy VARCHAR(16) NOT NULL DEFAULT 'STANDARD_PTE';

ALTER TABLE score_template_items
    ADD COLUMN IF NOT EXISTS task_type_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_screen_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_mode VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_authoring_contract_version INTEGER;

UPDATE score_template_items item
   SET task_type_key = definition.task_type_key
  FROM question_types definition
 WHERE definition.code = item.task_type
   AND item.task_type_key IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM score_template_items WHERE task_type_key IS NULL) THEN
        RAISE EXCEPTION 'Score-template task-key backfill found unresolved rows';
    END IF;
END $$;

ALTER TABLE score_template_items ALTER COLUMN task_type_key SET NOT NULL;
ALTER TABLE score_template_items ALTER COLUMN task_type DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_score_template_items_task_type_key
    ON score_template_items (task_type_key);

UPDATE score_template_items item
   SET runtime_screen_key = definition.screen_key,
       runtime_contract_version = definition.runtime_profile_version,
       runtime_scoring_mode = definition.runtime_scoring_mode,
       runtime_authoring_contract_key = definition.authoring_contract_key,
       runtime_authoring_contract_version = definition.authoring_contract_version
  FROM question_types definition
 WHERE definition.task_type_key = item.task_type_key
   AND item.runtime_screen_key IS NULL;
