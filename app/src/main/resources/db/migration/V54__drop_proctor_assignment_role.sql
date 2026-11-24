-- Proctors are no longer split into lead and assistant: every assignment is just a proctor on the session.
-- Dropping the column also drops its CHECK constraint (exam_session_proctors_role_check).
ALTER TABLE exam_session_proctors DROP COLUMN role;
