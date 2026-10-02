-- Keep the enrollment-owned roster view useful for Host account inspection
-- without exposing authentication secrets. Password hashes remain identity
-- internal and are never part of this view.
CREATE OR REPLACE VIEW identity_student_directory AS
SELECT u.id,
       u.public_id,
       u.email,
       u.full_name,
       u.student_code,
       u.phone,
       u.status,
       u.created_at,
       u.tenant_id,
       u.deleted,
       u.username,
       u.must_change_password
FROM users u
JOIN user_roles ur ON ur.user_id = u.id AND ur.role = 'STUDENT';
