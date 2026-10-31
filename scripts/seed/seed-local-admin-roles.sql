-- Local-only seed: one account per admin-portal role that V1__baseline.sql does not create
-- (V1 already seeds admin@test.local = PLATFORM_ADMIN).
-- Password hash is the same one V1 uses for every local test account, so the password is
-- the shared local one (see the "Local test accounts" note). Safe to re-run.
-- Run: docker exec -i pte-postgres psql -U pte-db -d pte < scripts/seed/seed-local-admin-roles.sql

INSERT INTO users (public_id, username, email, full_name, credential_hash, status, deleted, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(), s.username, s.username, s.full_name,
       '$2a$10$yQp01D6JXi84mEkwhIoBM.juAMwdJWzw8mxK94d35ru4oUpyjU0Hy', 'ACTIVE', false, false, now(), now()
FROM (VALUES ('manager@test.local', 'Platform Manager'),
             ('academic.manager@test.local', 'Academic Manager'),
             ('academic.staff@test.local', 'Academic Staff')) AS s(username, full_name)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = s.username);

INSERT INTO user_roles (user_id, role)
SELECT u.id, s.role
FROM (VALUES ('manager@test.local', 'PLATFORM_MANAGER'),
             ('academic.manager@test.local', 'ACADEMIC_MANAGER'),
             ('academic.staff@test.local', 'ACADEMIC_STAFF')) AS s(username, role)
JOIN users u ON u.username = s.username
WHERE NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = s.role);
