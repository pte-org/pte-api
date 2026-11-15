-- Student-facing answer hints (how to approach the task, never the answer itself).
ALTER TABLE questions
    ADD COLUMN answer_hint_vi TEXT,
    ADD COLUMN answer_hint_en TEXT;

-- Pinned into each practice item at session creation, like prompt_text.
ALTER TABLE practice_responses
    ADD COLUMN answer_hint_vi TEXT,
    ADD COLUMN answer_hint_en TEXT;
