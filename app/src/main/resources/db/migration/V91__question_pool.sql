-- Split the question bank into an EXAM pool (exam generation) and a PRACTICE
-- pool (practice module). Every existing question was authored for exams.
-- A question's pool is fixed at creation: the trigger rejects any later change.

ALTER TABLE questions
    ADD COLUMN pool VARCHAR(16) NOT NULL DEFAULT 'EXAM';

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_pool CHECK (pool IN ('EXAM', 'PRACTICE'));

CREATE INDEX idx_questions_pool ON questions (pool);

CREATE OR REPLACE FUNCTION questions_pool_immutable() RETURNS trigger AS $$
BEGIN
    IF NEW.pool IS DISTINCT FROM OLD.pool THEN
        RAISE EXCEPTION 'questions.pool is immutable (question %)', OLD.public_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_questions_pool_immutable
    BEFORE UPDATE OF pool ON questions
    FOR EACH ROW EXECUTE FUNCTION questions_pool_immutable();
