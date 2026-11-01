-- Credential changes ask whether a Student has an attempt in progress (lookup by student, then status).
-- Neither idx_attempts_tenant nor the unique key led by session_public_id can serve that lookup.
CREATE INDEX IF NOT EXISTS idx_attempts_student_status
    ON public.exam_attempts USING btree (student_public_id, status);
