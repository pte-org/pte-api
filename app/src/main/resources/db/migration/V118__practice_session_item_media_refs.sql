ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS audio_prompt_ref UUID,
    ADD COLUMN IF NOT EXISTS image_prompt_ref UUID,
    ADD COLUMN IF NOT EXISTS reference_answer_text TEXT,
    ADD COLUMN IF NOT EXISTS correct_answer_text TEXT;
