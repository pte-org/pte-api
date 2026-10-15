-- Overview host dashboard (plans/ninh-overview-dashboards, Phase 2).
-- Indexes only; no column or data change. Idempotent and partial on
-- "deleted = false" (same shape as V93/V94) because every dashboard query filters
-- soft deletes. Rollback, if required: drop these two indexes.

-- Reports awaiting publication: the count runs uncached on every dashboard request.
-- The only existing index leads with student_public_id, so a tenant-level count could not
-- use it. This partial index holds just the unpublished rows, which stay few.
CREATE INDEX IF NOT EXISTS idx_attempt_reports_tenant_unpublished
    ON attempt_reports (tenant_id) WHERE published = false AND deleted = false;

-- Recent activity: newest 8 audit rows of a tenant, run uncached on every request. The
-- existing indexes lead with tenant_id but carry no created_at, forcing a sort of the
-- tenant's entire audit history.
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_created
    ON audit_logs (tenant_id, created_at DESC) WHERE deleted = false;
