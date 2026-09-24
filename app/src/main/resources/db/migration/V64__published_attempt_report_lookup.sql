CREATE INDEX IF NOT EXISTS idx_attempt_reports_student_tenant_published
    ON attempt_reports (student_public_id, tenant_id, published, published_at);
