-- Pinned question content AI scoring reads from the scoring row instead of the queue message.
-- Rows ingested earlier stay NULL until the next host scoring request backfills them.

ALTER TABLE scoring_answers
    ADD COLUMN prompt_text TEXT,
    ADD COLUMN reference_answer_text TEXT,
    ADD COLUMN image_prompt_ref UUID,
    ADD COLUMN min_word_count INTEGER,
    ADD COLUMN max_word_count INTEGER;
