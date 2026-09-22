-- Additive logical task identity and immutable catalog metadata.
ALTER TABLE question_types
    ADD COLUMN IF NOT EXISTS task_type_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS normalized_display_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS screen_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_profile_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_behavior_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_renderer_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS runtime_answer_schema_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_scoring_profile_version INTEGER,
    ADD COLUMN IF NOT EXISTS runtime_scoring_mode VARCHAR(16),
    ADD COLUMN IF NOT EXISTS runtime_required_capabilities TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS runtime_min_app_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS authoring_contract_key VARCHAR(96),
    ADD COLUMN IF NOT EXISTS authoring_contract_version INTEGER,
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS first_published_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS runtime_locked_at TIMESTAMP WITH TIME ZONE;

-- V40 already canonicalizes the three historical aliases. This update is
-- intentionally deterministic and never invents a value for an unknown row.
UPDATE question_types
   SET task_type_key = CASE code
       WHEN 'FILL_BLANKS_READING_WRITING' THEN 'FILL_IN_THE_BLANKS_DROPDOWN'
       WHEN 'FILL_BLANKS_READING' THEN 'FILL_IN_THE_BLANKS_DRAG_AND_DROP'
       WHEN 'FILL_BLANKS_LISTENING' THEN 'FILL_IN_THE_BLANKS_TYPE_IN'
       ELSE upper(trim(code))
   END
 WHERE task_type_key IS NULL;

UPDATE question_types
   SET normalized_display_name = lower(
       regexp_replace(trim(display_name), '[[:space:]]+', ' ', 'g'))
 WHERE normalized_display_name IS NULL;

ALTER TABLE question_types
    ALTER COLUMN task_type_key SET NOT NULL,
    ALTER COLUMN normalized_display_name SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_question_types_task_type_key
    ON question_types (task_type_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_question_types_normalized_display_name
    ON question_types (normalized_display_name);
CREATE INDEX IF NOT EXISTS idx_question_types_screen_contract
    ON question_types (screen_key, runtime_profile_version);

DO $$
DECLARE duplicate_keys TEXT;
DECLARE duplicate_names TEXT;
BEGIN
    SELECT string_agg(task_type_key, ', ' ORDER BY task_type_key)
      INTO duplicate_keys
      FROM (
        SELECT task_type_key FROM question_types
        GROUP BY task_type_key HAVING COUNT(*) > 1
      ) duplicates;
    IF duplicate_keys IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate task_type_key values after normalization: %', duplicate_keys;
    END IF;

    SELECT string_agg(normalized_display_name, ', ' ORDER BY normalized_display_name)
      INTO duplicate_names
      FROM (
        SELECT normalized_display_name FROM question_types
        GROUP BY normalized_display_name HAVING COUNT(*) > 1
      ) duplicates;
    IF duplicate_names IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate normalized display names after normalization: %', duplicate_names;
    END IF;
END $$;
