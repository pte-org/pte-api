-- Local-schema profile only: platform admin and the e2e host. BCrypt hash only, no plaintext.
INSERT INTO public.users (public_id, username, email, full_name, credential_hash, status, deleted, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(), 'admin@test.local', 'admin@test.local', 'Bootstrap Admin', '$2a$10$yQp01D6JXi84mEkwhIoBM.juAMwdJWzw8mxK94d35ru4oUpyjU0Hy', 'ACTIVE', false, false, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE username = 'admin@test.local')
@@
INSERT INTO public.user_roles (user_id, role)
SELECT u.id, 'PLATFORM_ADMIN' FROM public.users u
WHERE u.username = 'admin@test.local'
  AND NOT EXISTS (SELECT 1 FROM public.user_roles r WHERE r.user_id = u.id AND r.role = 'PLATFORM_ADMIN')
@@
INSERT INTO public.organizations (public_id, code, name, organization_type, status, package_name, student_limit,
                                  facility_type, contact_email, deleted, version, created_at, updated_at)
SELECT gen_random_uuid(), 'e2e', 'E2E Test Organization', 'SCHOOL', 'ACTIVE', 'STANDARD', 100,
       'MAIN', 'host.e2e@test.local', false, 0, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.organizations WHERE code = 'e2e')
@@
INSERT INTO public.users (public_id, username, email, full_name, credential_hash, status, deleted, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(), 'host.e2e@test.local', 'host.e2e@test.local', 'E2E Host Admin', '$2a$10$yQp01D6JXi84mEkwhIoBM.juAMwdJWzw8mxK94d35ru4oUpyjU0Hy', 'ACTIVE', false, false, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE username = 'host.e2e@test.local')
@@
UPDATE public.users u SET organization_id = o.public_id
FROM public.organizations o
WHERE u.username = 'host.e2e@test.local' AND o.code = 'e2e' AND u.organization_id IS NULL
@@
INSERT INTO public.user_roles (user_id, role)
SELECT u.id, 'HOST_ADMIN' FROM public.users u
WHERE u.username = 'host.e2e@test.local'
  AND NOT EXISTS (SELECT 1 FROM public.user_roles r WHERE r.user_id = u.id AND r.role = 'HOST_ADMIN')
@@
