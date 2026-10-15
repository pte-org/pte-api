-- Overview admin dashboard revenue (plans/ninh-overview-dashboards, Phase 4).
-- Index only; no column or data change. Idempotent. The revenue section sums PAID orders over
-- a paid_at range per currency (about 14 range queries per cache miss: two windows plus a 12-month
-- series). The existing orders indexes lead with (tenant_id, created_at) or (status, created_at),
-- so none can serve a paid_at range. The index is partial on exactly the rows the queries read
-- (paid and not soft-deleted), so it stays small and costs nothing on pending, cancelled or
-- expired orders. Rollback, if required: drop this index.
CREATE INDEX IF NOT EXISTS idx_orders_paid_at
    ON orders (paid_at) WHERE status = 'PAID' AND deleted = false;
