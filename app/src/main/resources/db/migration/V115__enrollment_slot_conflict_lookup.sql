-- Phase 12: support tenant-wide student slot conflict checks during enrollment.
CREATE INDEX IF NOT EXISTS idx_sessions_tenant_window
    ON exam_sessions (tenant_id, opens_at, closes_at);
