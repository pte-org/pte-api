-- Overview admin dashboard (plans/ninh-overview-dashboards, Phase 3).
-- Indexes only; no column or data change. Idempotent and partial on "deleted = false"
-- (same shape as V93-V95) because every dashboard query filters soft deletes. These are
-- the platform-wide (no tenant prefix) period/grouped queries the admin dashboard runs;
-- V94 only covers tenant-leading host queries. Rollback, if required: drop these six indexes.

-- Attempts: platform-wide distinct active students (started in window).
CREATE INDEX IF NOT EXISTS idx_attempts_started
    ON exam_attempts (started_at) WHERE deleted = false;

-- Attempts: platform-wide submitted-in-window counts (later admin sections).
CREATE INDEX IF NOT EXISTS idx_attempts_submitted
    ON exam_attempts (submitted_at) WHERE deleted = false;

-- Subscriptions: usable / expiring-soon counts (status, then expiry range).
CREATE INDEX IF NOT EXISTS idx_subscriptions_status_expires
    ON subscriptions (status, expires_at) WHERE deleted = false;

-- Support tickets: platform-wide status counts and oldest-open list.
CREATE INDEX IF NOT EXISTS idx_support_ticket_status_created
    ON support_ticket (status, created_at) WHERE deleted = false;

-- Tenant applications: pending count, oldest-pending list, received-in-window.
CREATE INDEX IF NOT EXISTS idx_tenant_applications_status_created
    ON tenant_applications (status, created_at) WHERE deleted = false;

-- Tenants: created-in-window counts and the 12-month growth series, newest tenants.
CREATE INDEX IF NOT EXISTS idx_tenants_created
    ON tenants (created_at) WHERE deleted = false;
