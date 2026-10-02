-- Renames 3 PteTaskType constants to disambiguate the three different
-- fill-blanks input mechanisms (Reading dropdown, Reading drag-and-drop,
-- Listening type-in), per the updated APEUni V5 reference table:
--   FILL_BLANKS_READING_WRITING -> FILL_IN_THE_BLANKS_DROPDOWN
--   FILL_BLANKS_READING         -> FILL_IN_THE_BLANKS_DRAG_AND_DROP
--   FILL_BLANKS_LISTENING       -> FILL_IN_THE_BLANKS_TYPE_IN
--
-- Every table that stores this identifier as a plain string (or an
-- EnumType.STRING column) needs its existing rows updated in place, since
-- the Java enum constant no longer exists under the old name. Safe to run
-- even where a table has zero matching rows (system not yet deployed).

-- V37 installed this foreign key before the catalog was populated. PostgreSQL
-- still enforces NOT VALID foreign keys for new updates, so defer the
-- constraint until V44 has seeded and canonicalized question_types.
ALTER TABLE questions DROP CONSTRAINT IF EXISTS fk_questions_question_type;

UPDATE score_template_items SET task_type = 'FILL_IN_THE_BLANKS_DROPDOWN' WHERE task_type = 'FILL_BLANKS_READING_WRITING';
UPDATE score_template_items SET task_type = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP' WHERE task_type = 'FILL_BLANKS_READING';
UPDATE score_template_items SET task_type = 'FILL_IN_THE_BLANKS_TYPE_IN' WHERE task_type = 'FILL_BLANKS_LISTENING';

UPDATE questions SET pte_task_type = 'FILL_IN_THE_BLANKS_DROPDOWN' WHERE pte_task_type = 'FILL_BLANKS_READING_WRITING';
UPDATE questions SET pte_task_type = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP' WHERE pte_task_type = 'FILL_BLANKS_READING';
UPDATE questions SET pte_task_type = 'FILL_IN_THE_BLANKS_TYPE_IN' WHERE pte_task_type = 'FILL_BLANKS_LISTENING';

UPDATE snapshot_items SET pte_task_type = 'FILL_IN_THE_BLANKS_DROPDOWN' WHERE pte_task_type = 'FILL_BLANKS_READING_WRITING';
UPDATE snapshot_items SET pte_task_type = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP' WHERE pte_task_type = 'FILL_BLANKS_READING';
UPDATE snapshot_items SET pte_task_type = 'FILL_IN_THE_BLANKS_TYPE_IN' WHERE pte_task_type = 'FILL_BLANKS_LISTENING';

UPDATE pinned_items SET task_type = 'FILL_IN_THE_BLANKS_DROPDOWN' WHERE task_type = 'FILL_BLANKS_READING_WRITING';
UPDATE pinned_items SET task_type = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP' WHERE task_type = 'FILL_BLANKS_READING';
UPDATE pinned_items SET task_type = 'FILL_IN_THE_BLANKS_TYPE_IN' WHERE task_type = 'FILL_BLANKS_LISTENING';

UPDATE scoring_answers SET task_type = 'FILL_IN_THE_BLANKS_DROPDOWN' WHERE task_type = 'FILL_BLANKS_READING_WRITING';
UPDATE scoring_answers SET task_type = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP' WHERE task_type = 'FILL_BLANKS_READING';
UPDATE scoring_answers SET task_type = 'FILL_IN_THE_BLANKS_TYPE_IN' WHERE task_type = 'FILL_BLANKS_LISTENING';

-- V44 owns question_types catalog population and legacy-code canonicalization.
-- The question-type foreign key is recreated there after those rows exist.
