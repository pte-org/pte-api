-- Old local-schema seeds appended identical value-collection rows on every boot.
-- Compare every column: differing configurations must not be silently discarded.
WITH copies AS (
    SELECT ctid, row_number() OVER (PARTITION BY to_jsonb(item) ORDER BY ctid) AS copy_number
    FROM exam_definition_items item
)
DELETE FROM exam_definition_items item
USING copies
WHERE item.ctid = copies.ctid AND copies.copy_number > 1;

-- If different configurations still share a task key, fail transactionally for review.
CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_definition_items_task_key
    ON exam_definition_items (exam_definition_id, task_type_key);
