-- A source question can have at most one revision actively working its way through
-- DRAFT/PENDING_APPROVAL at a time. Without this, two concurrent "create revision"
-- calls for the same APPROVED question (e.g. a double-click, or a dev-mode effect
-- firing twice) each pass the "still APPROVED and current" guard and both insert a
-- revision row with the same supersedes_public_id — one of them later fails with
-- uq_questions_current_revision_group instead of a clear, immediate conflict here.
CREATE UNIQUE INDEX IF NOT EXISTS uq_questions_active_revision_per_source
    ON questions (supersedes_public_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL') AND deleted = FALSE;
