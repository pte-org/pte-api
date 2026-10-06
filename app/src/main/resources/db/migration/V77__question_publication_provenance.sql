-- Unknown history remains NULL. DRAFT/ARCHIVED alone cannot prove never published.
ALTER TABLE questions ADD COLUMN ever_published BOOLEAN;
UPDATE questions SET ever_published = TRUE WHERE status = 'APPROVED';

-- Keep provenance monotonic, including writes from an older app during rollout.
CREATE FUNCTION retain_question_publication_history() RETURNS trigger AS $$
BEGIN
    IF NEW.status = 'APPROVED' OR (TG_OP = 'UPDATE' AND OLD.ever_published IS TRUE) THEN
        NEW.ever_published := TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER question_publication_history
BEFORE INSERT OR UPDATE ON questions
FOR EACH ROW EXECUTE FUNCTION retain_question_publication_history();
