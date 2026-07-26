-- Phase 9: new ScoringAnswerStatus values (AI_SCORING, AI_SCORED_PENDING_REVIEW,
-- SCORING_FAILED) exceed the original VARCHAR(20) — "AI_SCORED_PENDING_REVIEW"
-- is 24 characters. Widen rather than risk silent truncation.
ALTER TABLE scoring_answers ALTER COLUMN status TYPE VARCHAR(40);
