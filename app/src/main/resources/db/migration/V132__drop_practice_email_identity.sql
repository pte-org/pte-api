-- Practice now signs in with the roster account and password an organization issued
-- (plan hung-practice-password-login), so the passwordless email identity is gone.
-- Destructive: drops the email challenge and email-identity tables and the session column that pointed at them.
-- Existing practice sessions stay owned by student_public_id; tenantless shell users created by the old flow
-- have no password and can no longer sign in.

DROP TABLE IF EXISTS practice_email_challenges;
DROP TABLE IF EXISTS practice_identity_memberships;
DROP TABLE IF EXISTS practice_identities;

ALTER TABLE practice_sessions DROP COLUMN IF EXISTS identity_public_id;
