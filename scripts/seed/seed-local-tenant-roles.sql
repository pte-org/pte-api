-- Local-only seed: PROCTOR and EXAMINER accounts in the same organization as host.e2e@test.local
-- (organization code 'e2e', created by V1__baseline.sql). HOST_ADMIN and STUDENT already exist there.
-- Same password hash as every V1 local test account. Safe to re-run.
-- Run: docker exec -i pte-postgres psql -U pte-db -d pte < scripts/seed/seed-local-tenant-roles.sql

INSERT INTO users (public_id, username, email, full_name, credential_hash, status, deleted, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(), s.username, s.username, s.full_name,
       '$2a$10$yQp01D6JXi84mEkwhIoBM.juAMwdJWzw8mxK94d35ru4oUpyjU0Hy', 'ACTIVE', false, false, now(), now()
FROM (VALUES ('proctor.e2e@test.local', 'E2E Proctor'),
             ('examiner.e2e@test.local', 'E2E Examiner')) AS s(username, full_name)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = s.username);

INSERT INTO organization_members (public_id, user_id, organization_id, status, deleted, created_at, updated_at)
SELECT gen_random_uuid(), u.id, o.public_id, 'ACTIVE', false, now(), now()
FROM users u, organizations o
WHERE u.username IN ('proctor.e2e@test.local', 'examiner.e2e@test.local') AND o.code = 'e2e'
  AND NOT EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = u.id AND m.organization_id = o.public_id);

INSERT INTO organization_member_roles (member_id, role)
SELECT m.id, s.role
FROM (VALUES ('proctor.e2e@test.local', 'PROCTOR'),
             ('examiner.e2e@test.local', 'EXAMINER')) AS s(username, role)
JOIN users u ON u.username = s.username
JOIN organization_members m ON m.user_id = u.id
JOIN organizations o ON o.public_id = m.organization_id AND o.code = 'e2e'
WHERE NOT EXISTS (SELECT 1 FROM organization_member_roles r WHERE r.member_id = m.id AND r.role = s.role);
