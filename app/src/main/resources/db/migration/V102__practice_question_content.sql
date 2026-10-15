-- Pin student-safe question content to a practice session item.
-- Correct-answer and authoring-only fields are intentionally not copied.

ALTER TABLE practice_session_items
    ADD COLUMN source_question_public_id UUID,
    ADD COLUMN prompt_text TEXT,
    ADD COLUMN options_json TEXT,
    ADD COLUMN min_word_count INTEGER,
    ADD COLUMN max_word_count INTEGER;

CREATE INDEX idx_practice_session_items_source_question
    ON practice_session_items (source_question_public_id);
