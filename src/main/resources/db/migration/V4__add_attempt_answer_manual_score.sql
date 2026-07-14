-- Schema reference: manual_score + optimistic-lock version on attempt_answer
ALTER TABLE attempt_answer ADD COLUMN IF NOT EXISTS manual_score NUMERIC(5,2);
ALTER TABLE attempt_answer ADD COLUMN IF NOT EXISTS version      BIGINT NOT NULL DEFAULT 0;

-- Composite index for efficient answer lookups by (attempt_id, question_id)
CREATE INDEX IF NOT EXISTS idx_attempt_answer_attempt_question ON attempt_answer(attempt_id, question_id);
