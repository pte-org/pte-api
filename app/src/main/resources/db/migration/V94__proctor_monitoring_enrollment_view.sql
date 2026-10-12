-- session module: exported monitoring enrollment membership projection.
-- Reads owner-only Session tables (enrollments, exam_sessions).
-- Other modules (specifically attempt) query only this view, never raw session tables.
-- Excludes soft-deleted enrollments, soft-deleted sessions, and cross-tenant mismatches.

CREATE OR REPLACE VIEW session_monitoring_enrollment_membership AS
SELECT
    e.tenant_id AS tenant_id,
    s.public_id AS exam_session_public_id,
    e.student_public_id AS student_public_id,
    e.public_id AS enrollment_public_id
FROM enrollments e
JOIN exam_sessions s ON s.id = e.session_id
WHERE e.deleted = FALSE
  AND s.deleted = FALSE
  AND e.tenant_id = s.tenant_id;
