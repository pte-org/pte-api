-- Platform users have tenant_id NULL, so the existing composite username index
-- does not enforce uniqueness for them on PostgreSQL. Keep one active login key
-- per platform account while leaving tenant username semantics unchanged.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_platform_username
    ON users (username)
    WHERE tenant_id IS NULL AND deleted = false;
