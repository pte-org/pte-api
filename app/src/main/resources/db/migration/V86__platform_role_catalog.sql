-- Add platform operational and academic roles without removing the legacy
-- PLATFORM_AUTHOR alias. The alias is cleaned up only after the compatibility
-- window and data backfill have been verified.

ALTER TABLE user_roles
    DROP CONSTRAINT IF EXISTS chk_user_roles_role;

ALTER TABLE user_roles
    ADD CONSTRAINT chk_user_roles_role
    CHECK (role IN ('PLATFORM_ADMIN', 'PLATFORM_MANAGER', 'ACADEMIC_MANAGER',
                    'ACADEMIC_STAFF', 'PLATFORM_AUTHOR', 'HOST_ADMIN',
                    'PROCTOR', 'EXAMINER', 'STUDENT'));

CREATE INDEX IF NOT EXISTS idx_user_roles_role
    ON user_roles (role);
