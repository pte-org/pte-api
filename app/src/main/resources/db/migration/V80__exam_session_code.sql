-- plans/phat-exam-session-code Phase 1: human-readable exam code shown to
-- hosts and typed by students instead of the 36-char public_id UUID.
-- Format {TENANT}-{YYMMDD}-{RAND4}, e.g. FPT-261010-K7QM; immutable after
-- creation and unique across all tenants (soft-deleted rows included).
-- Must stay in sync with SessionCodeGenerator.

ALTER TABLE exam_sessions ADD COLUMN session_code VARCHAR(24);

-- Temporary helper, dropped at the end. A function call in the UPDATE's SET
-- runs once per row; an uncorrelated scalar subquery would run once in total
-- and give every row the same suffix.
CREATE FUNCTION v80_random_session_suffix() RETURNS VARCHAR AS $$
DECLARE
    alphabet CONSTANT TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    result TEXT := '';
BEGIN
    FOR i IN 1..4 LOOP
        result := result || substr(alphabet, 1 + floor(random() * 32)::INT, 1);
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Tenant prefix falls back to PTE when the tenant row is missing.
UPDATE exam_sessions s
SET session_code = COALESCE(
            NULLIF(left(upper(replace(
                (SELECT t.code FROM tenants t WHERE t.public_id = s.tenant_id), '-', '')), 8), ''),
            'PTE')
        || '-' || to_char(s.opens_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYMMDD')
        || '-' || v80_random_session_suffix();

-- Re-randomize the suffix of every duplicate except the first row (by id) of
-- each code until none remain.
DO $$
DECLARE
    iteration INT := 0;
    duplicates INT;
BEGIN
    LOOP
        UPDATE exam_sessions s
        SET session_code = left(s.session_code, length(s.session_code) - 4) || v80_random_session_suffix()
        FROM (
            SELECT id, row_number() OVER (PARTITION BY session_code ORDER BY id) AS rn
            FROM exam_sessions
        ) ranked
        WHERE ranked.id = s.id AND ranked.rn > 1;
        GET DIAGNOSTICS duplicates = ROW_COUNT;
        EXIT WHEN duplicates = 0;
        iteration := iteration + 1;
        IF iteration >= 20 THEN
            RAISE EXCEPTION 'V80: session_code duplicates remain after % iterations', iteration;
        END IF;
    END LOOP;
END;
$$;

DROP FUNCTION v80_random_session_suffix();

ALTER TABLE exam_sessions ALTER COLUMN session_code SET NOT NULL;
ALTER TABLE exam_sessions ADD CONSTRAINT uq_exam_sessions_session_code UNIQUE (session_code);
