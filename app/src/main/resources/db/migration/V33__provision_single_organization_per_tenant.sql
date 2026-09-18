-- Every Host now owns one Organization, provisioned automatically at Tenant
-- creation. Backfill the default Organization for existing Tenants that do not
-- have one yet; existing rows are preserved.

INSERT INTO organizations (
    public_id,
    created_at,
    updated_at,
    deleted,
    tenant_id,
    name,
    address,
    facility_type,
    status
)
SELECT gen_random_uuid(),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE,
       t.id,
       t.name,
       NULL,
       'MAIN',
       'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.tenant_id = t.id
);

-- The existing data set has at most one Organization per Tenant. Enforce the
-- new one-Host/one-Organization invariant at the database boundary as well.
CREATE UNIQUE INDEX IF NOT EXISTS uk_organizations_tenant
    ON organizations (tenant_id);
