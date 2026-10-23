ALTER TABLE practice_explanations
    ADD COLUMN IF NOT EXISTS limitations_json TEXT;
