-- Overview host dashboard (plans/ninh-overview-dashboards, Phase 1).
-- Indexes only: tenant-leading composites for the period/grouped counts the host
-- dashboard runs on every request. No column or data change. Idempotent, and
-- partial on "deleted = false" because every dashboard query filters soft deletes
-- (same shape as V93). Rollback, if required: drop these six indexes.

-- Attempts: submitted-in-window counts and the 12-month series.
CREATE INDEX IF NOT EXISTS idx_attempts_tenant_submitted
    ON exam_attempts (tenant_id, submitted_at) WHERE deleted = false;

-- Attempts: distinct active students (started-in-window).
CREATE INDEX IF NOT EXISTS idx_attempts_tenant_started
    ON exam_attempts (tenant_id, started_at) WHERE deleted = false;

-- Attempts: in-progress-now count.
CREATE INDEX IF NOT EXISTS idx_attempts_tenant_status
    ON exam_attempts (tenant_id, status) WHERE deleted = false;

-- Users: new-learners-in-window count (role filter joins user_roles by user_id).
CREATE INDEX IF NOT EXISTS idx_users_tenant_created
    ON users (tenant_id, created_at) WHERE deleted = false;

-- Sessions: grouped-by-status counts and created-in-window counts.
CREATE INDEX IF NOT EXISTS idx_sessions_tenant_status
    ON exam_sessions (tenant_id, status) WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_sessions_tenant_created
    ON exam_sessions (tenant_id, created_at) WHERE deleted = false;
