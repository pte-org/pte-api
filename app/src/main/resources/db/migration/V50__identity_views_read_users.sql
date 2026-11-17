-- 1 username = 1 organization, step 3: both identity views read users + user_roles only. Column names, order and
-- types are unchanged. organization_id IS NOT NULL keeps free practice students and platform accounts out.
CREATE OR REPLACE VIEW identity_staff_student_search_view AS
 SELECT u.organization_id AS tenant_id,
    u.public_id AS student_public_id,
    COALESCE(NULLIF(TRIM(BOTH FROM u.full_name), ''::text), (u.username)::text, (u.email)::text) AS display_name,
    u.email
   FROM users u
  WHERE u.deleted = false AND u.organization_id IS NOT NULL;

CREATE OR REPLACE VIEW identity_student_directory AS
 SELECT u.id,
    u.public_id,
    u.email,
    u.full_name,
    u.student_code,
    u.phone,
    u.status,
    u.created_at,
    u.organization_id AS tenant_id,
    u.deleted,
    u.username,
    u.must_change_password
   FROM users u
     JOIN user_roles ur ON ur.user_id = u.id AND ur.role::text = 'STUDENT'::text
  WHERE u.organization_id IS NOT NULL;
