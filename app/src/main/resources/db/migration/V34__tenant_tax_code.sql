-- Preserve the tax identifier used to verify an organization after its
-- application has been approved. Existing tenants may be null because this
-- field was not required before this migration; all new API writes require it.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS tax_code VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenants_tax_code
    ON tenants (tax_code)
    WHERE tax_code IS NOT NULL;
