-- V36 created the platform username index without the deleted-row predicate.
-- V87 could not replace that relation with CREATE INDEX IF NOT EXISTS, so
-- rebuild the index in a new migration without touching user data.

DROP INDEX IF EXISTS uk_users_platform_username;

CREATE UNIQUE INDEX uk_users_platform_username
    ON users (username)
    WHERE tenant_id IS NULL AND deleted = false;
