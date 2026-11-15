-- Task and skill practice may draw every published question (students submit question by question), so the
-- session's question count no longer has an upper bound; the server stores Integer.MAX_VALUE for "all questions".
ALTER TABLE practice_sessions DROP CONSTRAINT IF EXISTS ck_practice_sessions_question_count;
ALTER TABLE practice_sessions ADD CONSTRAINT ck_practice_sessions_question_count
    CHECK (question_count IS NULL OR question_count >= 1);
