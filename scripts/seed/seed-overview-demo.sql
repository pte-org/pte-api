-- Seed realistic test data for PTE Overview Dashboards (Host + Admin)

-- 1. Update existing exam session 2 to be currently active & upcoming
UPDATE exam_sessions 
SET opens_at = now() - interval '2 hours',
    closes_at = now() + interval '3 days'
WHERE id = 2;

-- 2. Add a second upcoming scheduled exam session for the current host tenant
INSERT INTO exam_sessions (
    public_id, created_at, updated_at, deleted, name, tenant_id,
    opens_at, closes_at, status, capacity, subscription_id, license_key,
    draft_version, max_retries_per_student, session_code
)
SELECT 
    'e7c2901a-8263-47a4-921b-51a80c98231a'::uuid,
    now() - interval '1 day',
    now() - interval '1 day',
    false,
    'PTE Academic Mock Test Series B',
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    now() + interval '5 days',
    now() + interval '6 days',
    'SCHEDULED',
    30,
    'cc13b47f-0051-4fa5-a3f2-c71ab2053580'::uuid,
    'PTE-LIC-LOCAL-01',
    0,
    0,
    'PTE-MOCK-B01'
WHERE NOT EXISTS (
    SELECT 1 FROM exam_sessions WHERE public_id = 'e7c2901a-8263-47a4-921b-51a80c98231a'::uuid
);

-- 3. Support Tickets for Host & Admin
INSERT INTO support_ticket (
    public_id, created_at, updated_at, deleted, version,
    tenant_id, submitter_user_public_id, category, description, status
)
SELECT 
    'a1b2c3d4-0001-4000-8000-000000000001'::uuid,
    now() - interval '4 hours',
    now() - interval '4 hours',
    false,
    0,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    '343bcc8e-ad28-477a-b32c-d82bf6b079a9'::uuid,
    'BUG',
    'Audio recording latency observed during mock speaking exam',
    'OPEN'
WHERE NOT EXISTS (
    SELECT 1 FROM support_ticket WHERE public_id = 'a1b2c3d4-0001-4000-8000-000000000001'::uuid
);

INSERT INTO support_ticket (
    public_id, created_at, updated_at, deleted, version,
    tenant_id, submitter_user_public_id, category, description, status
)
SELECT 
    'a1b2c3d4-0002-4000-8000-000000000002'::uuid,
    now() - interval '1 day',
    now() - interval '6 hours',
    false,
    1,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    '343bcc8e-ad28-477a-b32c-d82bf6b079a9'::uuid,
    'GENERAL_FEEDBACK',
    'Requesting upgrade to 100 student seats tier',
    'IN_PROGRESS'
WHERE NOT EXISTS (
    SELECT 1 FROM support_ticket WHERE public_id = 'a1b2c3d4-0002-4000-8000-000000000002'::uuid
);

-- 4. Billing Orders (Pending & Paid) for Host & Admin Revenue
INSERT INTO orders (
    public_id, created_at, updated_at, deleted,
    tenant_id, plan_id, order_code, amount, currency, status, paid_at
)
SELECT
    'f1a2b3c4-0001-4000-8000-000000000001'::uuid,
    now() - interval '3 days',
    now() - interval '3 days',
    false,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    '3d0c90ac-e57a-4875-b7b7-f1f30d2891f0'::uuid,
    1092801,
    15000000.00,
    'VND',
    'PAID',
    now() - interval '3 days'
WHERE NOT EXISTS (
    SELECT 1 FROM orders WHERE order_code = 1092801
);

INSERT INTO orders (
    public_id, created_at, updated_at, deleted,
    tenant_id, plan_id, order_code, amount, currency, status
)
SELECT
    'f1a2b3c4-0002-4000-8000-000000000002'::uuid,
    now() - interval '5 hours',
    now() - interval '5 hours',
    false,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    '3d0c90ac-e57a-4875-b7b7-f1f30d2891f0'::uuid,
    1092802,
    5000000.00,
    'VND',
    'PENDING'
WHERE NOT EXISTS (
    SELECT 1 FROM orders WHERE order_code = 1092802
);

-- 5. Exam Attempts for trend history in Host Attempts Chart
INSERT INTO exam_attempts (
    public_id, created_at, updated_at, deleted,
    session_public_id, student_public_id, tenant_id, status,
    started_at, submitted_at, current_order_index, play_count, attempt_number, max_retries_per_student
)
SELECT
    'c1d2e3f4-0001-4000-8000-000000000001'::uuid,
    now() - interval '45 days',
    now() - interval '45 days',
    false,
    '924f2797-7f16-4d3f-895f-048ad6f8e7f9'::uuid,
    '3f79aabc-e101-49c0-8bd2-ca03c3daf108'::uuid,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    'SUBMITTED',
    now() - interval '45 days',
    now() - interval '45 days' + interval '2 hours',
    10, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM exam_attempts WHERE public_id = 'c1d2e3f4-0001-4000-8000-000000000001'::uuid
);

INSERT INTO exam_attempts (
    public_id, created_at, updated_at, deleted,
    session_public_id, student_public_id, tenant_id, status,
    started_at, submitted_at, current_order_index, play_count, attempt_number, max_retries_per_student
)
SELECT
    'c1d2e3f4-0002-4000-8000-000000000002'::uuid,
    now() - interval '20 days',
    now() - interval '20 days',
    false,
    '924f2797-7f16-4d3f-895f-048ad6f8e7f9'::uuid,
    '788cbfb0-33e0-4711-8a0e-1f56e655eb04'::uuid,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    'SUBMITTED',
    now() - interval '20 days',
    now() - interval '20 days' + interval '2 hours',
    10, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM exam_attempts WHERE public_id = 'c1d2e3f4-0002-4000-8000-000000000002'::uuid
);

INSERT INTO exam_attempts (
    public_id, created_at, updated_at, deleted,
    session_public_id, student_public_id, tenant_id, status,
    started_at, submitted_at, current_order_index, play_count, attempt_number, max_retries_per_student
)
SELECT
    'c1d2e3f4-0003-4000-8000-000000000003'::uuid,
    now() - interval '2 days',
    now() - interval '2 days',
    false,
    '924f2797-7f16-4d3f-895f-048ad6f8e7f9'::uuid,
    'f78088f9-a9b6-4789-a844-376fe9ee1202'::uuid,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    'SUBMITTED',
    now() - interval '2 days',
    now() - interval '2 days' + interval '2 hours',
    10, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM exam_attempts WHERE public_id = 'c1d2e3f4-0003-4000-8000-000000000003'::uuid
);

INSERT INTO exam_attempts (
    public_id, created_at, updated_at, deleted,
    session_public_id, student_public_id, tenant_id, status,
    started_at, current_order_index, play_count, attempt_number, max_retries_per_student
)
SELECT
    'c1d2e3f4-0004-4000-8000-000000000004'::uuid,
    now() - interval '30 minutes',
    now() - interval '30 minutes',
    false,
    '924f2797-7f16-4d3f-895f-048ad6f8e7f9'::uuid,
    'd804d7b2-c811-445b-9759-ce506ebe2a97'::uuid,
    '68707e82-f742-4bb5-bc3e-fcaa682afe58'::uuid,
    'IN_PROGRESS',
    now() - interval '30 minutes',
    3, 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM exam_attempts WHERE public_id = 'c1d2e3f4-0004-4000-8000-000000000004'::uuid
);

-- 6. Additional Tenants for Platform Admin Overview (Org-type split, Growth chart, Recent Tenants)
INSERT INTO organizations (
    public_id, created_at, updated_at, deleted,
    code, name, organization_type, status, package_name, student_limit, facility_type, version
)
SELECT
    '11111111-2222-4000-8000-000000000001'::uuid,
    now() - interval '50 days',
    now() - interval '50 days',
    false,
    'fpt-danang',
    'FPT University Da Nang',
    'UNIVERSITY',
    'ACTIVE',
    'ENTERPRISE',
    200,
    'MAIN',
    0
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE code = 'fpt-danang'
);

INSERT INTO organizations (
    public_id, created_at, updated_at, deleted,
    code, name, organization_type, status, package_name, student_limit, facility_type, version
)
SELECT
    '11111111-2222-4000-8000-000000000002'::uuid,
    now() - interval '25 days',
    now() - interval '25 days',
    false,
    'ila-vietnam',
    'ILA English Vietnam',
    'ENTERPRISE',
    'ACTIVE',
    'PROFESSIONAL',
    100,
    'MAIN',
    0
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE code = 'ila-vietnam'
);

INSERT INTO organizations (
    public_id, created_at, updated_at, deleted,
    code, name, organization_type, status, package_name, student_limit, facility_type, version
)
SELECT
    '11111111-2222-4000-8000-000000000003'::uuid,
    now() - interval '8 days',
    now() - interval '8 days',
    false,
    'vus-center',
    'VUS English Academy',
    'SCHOOL',
    'SUSPENDED',
    'STARTER',
    50,
    'MAIN',
    0
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE code = 'vus-center'
);

-- Additional paid order for FPT Da Nang
INSERT INTO orders (
    public_id, created_at, updated_at, deleted,
    tenant_id, plan_id, order_code, amount, currency, status, paid_at
)
SELECT
    'f1a2b3c4-0003-4000-8000-000000000003'::uuid,
    now() - interval '12 days',
    now() - interval '12 days',
    false,
    '11111111-2222-4000-8000-000000000001'::uuid,
    '3d0c90ac-e57a-4875-b7b7-f1f30d2891f0'::uuid,
    1092803,
    35000000.00,
    'VND',
    'PAID',
    now() - interval '12 days'
WHERE NOT EXISTS (
    SELECT 1 FROM orders WHERE order_code = 1092803
);
