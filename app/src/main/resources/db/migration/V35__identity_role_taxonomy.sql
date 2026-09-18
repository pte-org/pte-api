-- Consolidate the identity role taxonomy.
-- HOST_AUTHOR becomes the single tenant administrator role and both former
-- tenant staff roles share the new EXAMINER role.

UPDATE user_roles
SET role = 'HOST_ADMIN'
WHERE role = 'HOST_AUTHOR';

UPDATE user_roles
SET role = 'EXAMINER'
WHERE role IN ('LECTURER', 'PROGRAM_COORDINATOR');

-- The old roles may have collapsed into the same role for one user. Remove
-- duplicate set elements before enforcing the new allowed-value constraint.
DELETE FROM user_roles first_role
USING user_roles duplicate_role
WHERE first_role.ctid < duplicate_role.ctid
  AND first_role.user_id = duplicate_role.user_id
  AND first_role.role = duplicate_role.role;

ALTER TABLE user_roles
    ADD CONSTRAINT chk_user_roles_role
    CHECK (role IN ('PLATFORM_ADMIN', 'PLATFORM_AUTHOR', 'HOST_ADMIN',
                    'PROCTOR', 'EXAMINER', 'STUDENT'));
