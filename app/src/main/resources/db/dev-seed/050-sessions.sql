-- Local-schema profile only: one scheduled exam for the e2e organization with three enrolled students and a proctor
-- slot left for the host to fill. The frozen exam has no items (the local question bank is empty), so it is enough to
-- exercise rosters, attendance and identity verification, not to sit the exam.
INSERT INTO public.exam_versions (public_id, name, version, source_blueprint_public_id, exam_definition_public_id,
                                  exam_definition_version, generation_algorithm_version, tenant_id, deleted,
                                  created_at, updated_at)
SELECT 'a1b2c3d4-0007-4000-8000-000000000001'::uuid, 'Local sample exam', 1, gen_random_uuid(), d.public_id, d.version,
       'PTE_SEEDED_V1', o.public_id, false, now(), now()
FROM public.exam_definitions d, public.organizations o
WHERE d.code = 'PTE_Score_Template' AND o.code = 'e2e'
  AND NOT EXISTS (SELECT 1 FROM public.exam_versions WHERE public_id = 'a1b2c3d4-0007-4000-8000-000000000001'::uuid)
@@
INSERT INTO public.exam_sessions (public_id, name, session_code, tenant_id, subscription_id, license_key,
                                  exam_version_public_id, exam_definition_public_id, exam_definition_version,
                                  exam_mode, reuse_policy, max_retries_per_student, draft_version, opens_at,
                                  closes_at, status, capacity, replay_policy_type, replay_policy_limit,
                                  device_check_required, proctor_required, answer_integrity_level, lockdown_mode,
                                  deleted, created_at, updated_at)
SELECT 'a1b2c3d4-0007-4000-8000-000000000002'::uuid, 'Local sample exam session', 'E2E-LOCAL-0001', o.public_id,
       s.public_id, s.license_key, v.public_id, v.exam_definition_public_id, v.exam_definition_version,
       'OFFICIAL_EXAM', 'ALLOW', 0, 0, now() + interval '30 days', now() + interval '30 days 2 hours', 'SCHEDULED',
       30, 'LIMITED', 1, true, true, 'STRICT', 'STRICT', false, now(), now()
FROM public.organizations o
JOIN public.subscriptions s ON s.tenant_id = o.public_id AND s.license_key = 'PTE-LOCAL-E2E-SUB-0001'
JOIN public.exam_versions v ON v.public_id = 'a1b2c3d4-0007-4000-8000-000000000001'::uuid
WHERE o.code = 'e2e'
  AND NOT EXISTS (SELECT 1 FROM public.exam_sessions WHERE public_id = 'a1b2c3d4-0007-4000-8000-000000000002'::uuid)
@@
INSERT INTO public.exam_registrations (public_id, session_id, tenant_id, student_public_id, license_key, status,
                                       published_at, attendance_status, attendance_version, identity_status,
                                       deleted, created_at, updated_at)
SELECT gen_random_uuid(), s.id, s.tenant_id, u.public_id, s.license_key, 'ENROLLED', now(), 'UNMARKED', 0,
       'UNVERIFIED', false, now(), now()
FROM public.exam_sessions s
JOIN public.users u ON u.username IN ('student1.e2e@test.local', 'student2.e2e@test.local', 'student3.e2e@test.local')
WHERE s.public_id = 'a1b2c3d4-0007-4000-8000-000000000002'::uuid
  AND NOT EXISTS (SELECT 1 FROM public.exam_registrations r WHERE r.session_id = s.id AND r.student_public_id = u.public_id)
