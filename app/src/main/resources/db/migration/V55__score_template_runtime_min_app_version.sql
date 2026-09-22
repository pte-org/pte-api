-- Preserve the minimum supported app version with the template's pinned
-- runtime contract. Existing rows are backfilled from the catalog contract;
-- historical templates remain readable while new snapshots carry the value.
ALTER TABLE score_template_items
    ADD COLUMN IF NOT EXISTS runtime_min_app_version VARCHAR(32);

UPDATE score_template_items item
   SET runtime_min_app_version = definition.runtime_min_app_version
  FROM question_types definition
 WHERE definition.task_type_key = item.task_type_key
   AND item.runtime_min_app_version IS NULL;
