-- A tenant-scoped username may be reused by different organizations.
-- Platform accounts remain unique among platform accounts through the partial
-- index; tenant accounts are unique only inside their own tenant.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_tenant_username
    ON users (tenant_id, username)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_platform_username
    ON users (username)
    WHERE tenant_id IS NULL;
