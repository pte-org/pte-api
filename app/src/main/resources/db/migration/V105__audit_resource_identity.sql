ALTER TABLE audit_logs
    ADD COLUMN resource_name VARCHAR(255),
    ADD COLUMN resource_code VARCHAR(255);

CREATE INDEX idx_audit_logs_tenant_resource_name
    ON audit_logs (tenant_id, resource_name);

CREATE INDEX idx_audit_logs_tenant_resource_code
    ON audit_logs (tenant_id, resource_code);

-- Existing exam-session events can be enriched without exposing their UUIDs.
UPDATE audit_logs a
SET resource_name = s.name,
    resource_code = s.session_code
FROM exam_sessions s
WHERE a.aggregate_type = 'ExamSession'
  AND a.aggregate_id = s.public_id::text
  AND a.resource_name IS NULL;
