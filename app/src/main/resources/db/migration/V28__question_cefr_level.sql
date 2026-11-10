-- CEFR level a question is written for. Null means not yet classified; questions that existed before this
-- migration stay null rather than being given a guessed level.
ALTER TABLE questions
    ADD COLUMN cefr_level character varying(2),
    ADD CONSTRAINT questions_cefr_level_check CHECK (cefr_level IS NULL OR cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'));
CREATE INDEX idx_questions_cefr_level ON questions (task_type_key, cefr_level);
