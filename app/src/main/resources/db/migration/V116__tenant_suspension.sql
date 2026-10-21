-- Phase 13: bounded tenant suspensions must retain their automatic resume time.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_tenants_suspension_expiry
    ON tenants (status, suspended_until) WHERE deleted = false;
