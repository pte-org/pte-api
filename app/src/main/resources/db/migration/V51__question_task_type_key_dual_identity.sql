-- Add logical task identity before relaxing legacy enum columns. All existing
-- question/snapshot/pinned rows are backfilled in this migration together.
ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS task_type_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS task_type_section VARCHAR(16);
ALTER TABLE snapshot_items
    ADD COLUMN IF NOT EXISTS task_type_key VARCHAR(64);
ALTER TABLE pinned_items
    ADD COLUMN IF NOT EXISTS task_type_key VARCHAR(64);

UPDATE questions question
   SET task_type_key = definition.task_type_key,
       task_type_section = definition.section
  FROM question_types definition
 WHERE definition.code = question.pte_task_type
   AND question.task_type_key IS NULL;

UPDATE snapshot_items item
   SET task_type_key = definition.task_type_key
  FROM question_types definition
 WHERE definition.code = COALESCE(item.task_type_code, item.pte_task_type)
   AND item.task_type_key IS NULL;

UPDATE pinned_items item
   SET task_type_key = definition.task_type_key
  FROM question_types definition
 WHERE definition.code = COALESCE(item.task_type_code, item.task_type)
   AND item.task_type_key IS NULL;

DO $$
DECLARE unresolved_questions INTEGER;
DECLARE unresolved_snapshots INTEGER;
DECLARE unresolved_pinned INTEGER;
BEGIN
    SELECT COUNT(*) INTO unresolved_questions FROM questions WHERE task_type_key IS NULL;
    SELECT COUNT(*) INTO unresolved_snapshots FROM snapshot_items WHERE task_type_key IS NULL;
    SELECT COUNT(*) INTO unresolved_pinned FROM pinned_items WHERE task_type_key IS NULL;
    IF unresolved_questions > 0 OR unresolved_snapshots > 0 OR unresolved_pinned > 0 THEN
        RAISE EXCEPTION 'Task-key backfill unresolved rows: questions=%, snapshots=%, pinned_items=%',
            unresolved_questions, unresolved_snapshots, unresolved_pinned;
    END IF;
    RAISE NOTICE 'Task-key backfill complete: questions=%, snapshots=%, pinned_items=%',
        (SELECT COUNT(*) FROM questions), (SELECT COUNT(*) FROM snapshot_items),
        (SELECT COUNT(*) FROM pinned_items);
END $$;

ALTER TABLE questions ALTER COLUMN task_type_key SET NOT NULL;
ALTER TABLE questions ALTER COLUMN task_type_section SET NOT NULL;
ALTER TABLE snapshot_items ALTER COLUMN task_type_key SET NOT NULL;
ALTER TABLE pinned_items ALTER COLUMN task_type_key SET NOT NULL;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_task_type_key
    FOREIGN KEY (task_type_key) REFERENCES question_types (task_type_key) NOT VALID;
ALTER TABLE snapshot_items
    ADD CONSTRAINT fk_snapshot_items_task_type_key
    FOREIGN KEY (task_type_key) REFERENCES question_types (task_type_key) NOT VALID;
ALTER TABLE pinned_items
    ADD CONSTRAINT fk_pinned_items_task_type_key
    FOREIGN KEY (task_type_key) REFERENCES question_types (task_type_key) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_questions_task_type_key ON questions (task_type_key);
CREATE INDEX IF NOT EXISTS idx_snapshot_items_task_type_key ON snapshot_items (task_type_key);
CREATE INDEX IF NOT EXISTS idx_pinned_items_task_type_key ON pinned_items (task_type_key);

-- Standard rows retain the legacy enum/code for old readers. Custom rows use
-- only task_type_key; nullable legacy columns are the explicit adapter seam.
ALTER TABLE questions ALTER COLUMN pte_task_type DROP NOT NULL;
ALTER TABLE snapshot_items ALTER COLUMN pte_task_type DROP NOT NULL;
ALTER TABLE pinned_items ALTER COLUMN task_type DROP NOT NULL;
