-- PTE pivot (Phase 1): questions gain a task-type-driven shape instead of the
-- single-skill APTIS model. skill is relaxed to nullable — it's deprecated for
-- new PTE questions (see Question.java Javadoc) but stays populated on archived
-- APTIS rows, which are not migrated forward.
ALTER TABLE questions ALTER COLUMN skill DROP NOT NULL;

ALTER TABLE questions ADD COLUMN IF NOT EXISTS pte_task_type VARCHAR(64);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS reference_answer_text TEXT;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS min_word_count INTEGER;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS max_word_count INTEGER;
