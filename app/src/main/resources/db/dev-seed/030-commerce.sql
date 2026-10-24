-- Local-schema profile only: one active exam package, an unredeemed license code and an ACTIVE subscription for the
-- seeded organization 'e2e'. The code below is a local demo value; the hosted stack never runs this profile.
INSERT INTO public.service_plans (public_id, name, description, type, price, currency, duration_days,
                                  max_students_per_session, status, version, deleted, created_at, updated_at)
SELECT 'a1b2c3d4-0003-4000-8000-000000000001'::uuid, 'PTE Local Exam Package', 'Seeded for local development',
       'EXAM_PACKAGE', 0, 'VND', 365, 50, 'ACTIVE', 0, false, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.service_plans WHERE public_id = 'a1b2c3d4-0003-4000-8000-000000000001'::uuid)
@@
INSERT INTO public.subscriptions (public_id, tenant_id, plan_id, license_key, starts_at, expires_at,
                                  max_students_per_session, status, activation_source, deleted, created_at, updated_at)
SELECT gen_random_uuid(), o.public_id, 'a1b2c3d4-0003-4000-8000-000000000001'::uuid, 'PTE-LOCAL-E2E-SUB-0001',
       now(), now() + interval '365 days', 50, 'ACTIVE', 'LICENSE_CODE', false, now(), now()
FROM public.organizations o
WHERE o.code = 'e2e'
  AND NOT EXISTS (SELECT 1 FROM public.subscriptions WHERE license_key = 'PTE-LOCAL-E2E-SUB-0001')
@@
INSERT INTO public.license_codes (public_id, code, plan_id, status, issued_by, issued_at, deleted, created_at, updated_at)
SELECT gen_random_uuid(), 'PTE-LOCAL-DEMO-0001', 'a1b2c3d4-0003-4000-8000-000000000001'::uuid, 'ISSUED',
       u.public_id, now(), false, now(), now()
FROM public.users u
WHERE u.username = 'admin@test.local'
  AND NOT EXISTS (SELECT 1 FROM public.license_codes WHERE code = 'PTE-LOCAL-DEMO-0001')
@@
