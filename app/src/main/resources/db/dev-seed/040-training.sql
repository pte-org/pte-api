-- Local-schema profile only: a sample program with two classes and four placed students for the e2e organization.
-- Students share the BCrypt hash of the other local accounts; no plaintext password is stored here.
INSERT INTO public.programs (public_id, organization_id, name, description, status, deleted, created_at, updated_at)
SELECT gen_random_uuid(), o.id, 'Luyện thi PTE Academic 50+', 'Khóa mẫu cho môi trường local', 'ACTIVE', false, now(), now()
FROM public.organizations o
WHERE o.code = 'e2e'
  AND NOT EXISTS (SELECT 1 FROM public.programs p WHERE p.organization_id = o.id AND p.name = 'Luyện thi PTE Academic 50+')
@@
INSERT INTO public.classes (public_id, program_id, organization_id, name, status, deleted, created_at, updated_at)
SELECT gen_random_uuid(), p.id, o.public_id, c.name, 'ACTIVE', false, now(), now()
FROM public.programs p
JOIN public.organizations o ON o.id = p.organization_id AND o.code = 'e2e'
CROSS JOIN (VALUES ('PTE-50A'), ('PTE-50B')) AS c(name)
WHERE p.name = 'Luyện thi PTE Academic 50+'
  AND NOT EXISTS (SELECT 1 FROM public.classes x WHERE x.program_id = p.id AND x.name = c.name)
@@
INSERT INTO public.users (public_id, username, email, full_name, student_code, credential_hash, status, deleted, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(), s.username, s.username, s.full_name, s.student_code,
       '$2a$10$yQp01D6JXi84mEkwhIoBM.juAMwdJWzw8mxK94d35ru4oUpyjU0Hy', 'ACTIVE', false, false, now(), now()
FROM (VALUES ('student1.e2e@test.local', 'Nguyễn Văn An', 'E2E-001'),
             ('student2.e2e@test.local', 'Trần Thị Bích Ngọc', 'E2E-002'),
             ('student3.e2e@test.local', 'Lê Hoàng Minh', 'E2E-003'),
             ('student4.e2e@test.local', 'Phạm Thu Hà', 'E2E-004')) AS s(username, full_name, student_code)
WHERE NOT EXISTS (SELECT 1 FROM public.users u WHERE u.username = s.username)
@@
UPDATE public.users u SET organization_id = o.public_id
FROM public.organizations o
WHERE o.code = 'e2e' AND u.username LIKE 'student_.e2e@test.local' AND u.organization_id IS NULL
@@
INSERT INTO public.user_roles (user_id, role)
SELECT u.id, 'STUDENT' FROM public.users u
WHERE u.username LIKE 'student_.e2e@test.local'
  AND NOT EXISTS (SELECT 1 FROM public.user_roles r WHERE r.user_id = u.id AND r.role = 'STUDENT')
@@
INSERT INTO public.class_students (public_id, class_id, student_public_id, organization_id, deleted, created_at, updated_at)
SELECT gen_random_uuid(), c.id, u.public_id, c.organization_id, false, now(), now()
FROM (VALUES ('student1.e2e@test.local', 'PTE-50A'), ('student2.e2e@test.local', 'PTE-50A'),
             ('student3.e2e@test.local', 'PTE-50B')) AS a(username, class_name)
JOIN public.users u ON u.username = a.username
JOIN public.classes c ON c.name = a.class_name
JOIN public.organizations o ON o.public_id = c.organization_id AND o.code = 'e2e'
WHERE NOT EXISTS (SELECT 1 FROM public.class_students x WHERE x.student_public_id = u.public_id)
