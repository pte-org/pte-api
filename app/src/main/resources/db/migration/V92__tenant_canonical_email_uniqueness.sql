-- Fail safely if legacy tenant users already collide; never clean up user data implicitly.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users
        WHERE tenant_id IS NOT NULL AND deleted = false AND nullif(trim(email), '') IS NOT NULL
        GROUP BY tenant_id, lower(trim(email)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'TENANT_CANONICAL_EMAIL_COLLISION_REQUIRES_REMEDIATION';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_users_tenant_canonical_email ON users (tenant_id, lower(trim(email)))
WHERE tenant_id IS NOT NULL AND deleted = false AND nullif(trim(email), '') IS NOT NULL;
