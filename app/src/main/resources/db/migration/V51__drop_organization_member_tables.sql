-- 1 username = 1 organization, step 4 (contract). Since V49 every account carries its organization in
-- users.organization_id and all its roles in user_roles; nothing reads or writes the member tables any more
-- (V50 rewired the views, phase 3/4 code no longer references them). Take a pg_dump and export
-- identity_account_split_log before applying: this is not reversible except from that dump.
DROP TABLE organization_member_roles;
DROP TABLE organization_members;
DROP TABLE identity_account_split_log;
