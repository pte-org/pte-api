ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS objective_options_json TEXT;
