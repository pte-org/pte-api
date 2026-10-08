-- Complete the compatibility-window data backfill after the canonical role
-- contract has been deployed and verified. Keep PLATFORM_AUTHOR in the
-- database check constraint/JVM enum for old tokens and rollback-safe reads;
-- only persisted role rows become canonical.

-- A legacy account may already have both names after an administrative role
-- update. Remove only that duplicate role row before the canonical update;
-- audit_logs and all other user history remain untouched.
DELETE FROM user_roles legacy_role
USING user_roles canonical_role
WHERE legacy_role.user_id = canonical_role.user_id
  AND legacy_role.role = 'PLATFORM_AUTHOR'
  AND canonical_role.role = 'ACADEMIC_STAFF'
  AND legacy_role.ctid < canonical_role.ctid;

UPDATE user_roles
SET role = 'ACADEMIC_STAFF'
WHERE role = 'PLATFORM_AUTHOR';
