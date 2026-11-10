-- The question title was an admin-only label that no candidate screen shows. Exam snapshots keep
-- their own copy of a title (now the task type name), so only the question column goes.
ALTER TABLE questions DROP COLUMN title;
