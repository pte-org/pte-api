-- 1 username = 1 organization, step 2 (backfill and split). One transaction: any failed assertion at the end
-- rolls everything back.
--
-- Rules (see plan phase 3):
--  * deleted users are never cloned or activated; a live user with only deleted memberships is marked deleted;
--  * a platform account (PLATFORM_ADMIN / PLATFORM_MANAGER / ACADEMIC_MANAGER / ACADEMIC_STAFF / legacy
--    PLATFORM_AUTHOR) keeps only its platform roles and no
--    organization; every membership becomes a new account;
--  * any other account keeps its oldest membership on the original row; each further membership becomes a new
--    account `{organization.code}.{8 random chars}` with a new public id and the same credential hash;
--  * tenant-scoped rows that point at the original public id move to the new public id for that organization;
--  * refresh tokens of split accounts are revoked and their reset tokens deleted.
DO $$
DECLARE
    platform_roles constant text[] := ARRAY['PLATFORM_ADMIN', 'PLATFORM_MANAGER', 'PLATFORM_AUTHOR',
                                                  'ACADEMIC_MANAGER', 'ACADEMIC_STAFF'];
    alphabet constant text := 'abcdefghjkmnpqrstuvwxyz23456789';
    -- {table, user column, tenant column}: reconciled against information_schema on 2026-11-15 (every uuid column
    -- that can hold a user public id). Tables with no tenant column (ai_prompt_versions, license_codes,
    -- notification_announcements, organization_quota_grants, question_generation_jobs, question_types,
    -- scoring_rubric_*, chat_usage, support_ticket_note, attempt_answers) stay with the original account.
    repoint constant text[] := ARRAY[
        ['answer_evaluations', 'examiner_public_id', 'tenant_id'],
        ['answer_evaluations', 'carried_over_from_examiner_public_id', 'tenant_id'],
        ['grading_assignments', 'examiner_public_id', 'tenant_id'],
        ['grading_assignments', 'assigned_by_public_id', 'tenant_id'],
        ['grading_issues', 'reported_by', 'tenant_id'],
        ['grading_issues', 'resolved_by', 'tenant_id'],
        ['exam_session_proctors', 'proctor_public_id', 'tenant_id'],
        ['exam_session_audience_sources', 'created_by', 'tenant_id'],
        ['exam_sessions', 'grading_frozen_by_public_id', 'tenant_id'],
        ['exam_registrations', 'last_actor_public_id', 'tenant_id'],
        ['exam_registrations', 'student_public_id', 'tenant_id'],
        ['exam_incidents', 'reporter_public_id', 'tenant_id'],
        ['exam_incidents', 'student_public_id', 'tenant_id'],
        ['exam_attempts', 'student_public_id', 'tenant_id'],
        ['exam_results', 'student_public_id', 'tenant_id'],
        ['exam_results', 'published_by_public_id', 'tenant_id'],
        ['class_students', 'student_public_id', 'organization_id'],
        ['practice_sessions', 'student_public_id', 'tenant_id'],
        ['practice_student_profiles', 'student_public_id', 'tenant_id'],
        ['audit_logs', 'actor_user_id', 'tenant_id'],
        ['media_objects', 'owner_public_id', 'tenant_id'],
        ['notification_inbox_snapshots', 'recipient_user_public_id', 'tenant_id'],
        ['notification_logs', 'recipient_user_public_id', 'tenant_id'],
        ['notification_recipient_streams', 'recipient_user_public_id', 'tenant_id'],
        ['notification_recipients', 'recipient_user_public_id', 'tenant_id'],
        ['exam_blueprints', 'author_user_public_id', 'tenant_id'],
        ['exam_definitions', 'author_user_public_id', 'tenant_id'],
        ['questions', 'author_user_public_id', 'tenant_id'],
        ['support_ticket', 'submitter_user_public_id', 'tenant_id']
    ];
    cur_user record;
    cur_member record;
    has_platform boolean;
    kept boolean;
    org_code text;
    candidate text;
    new_id bigint;
    new_public uuid;
    new_status text;
    bad bigint;
    i int;
    attempt int;
    split_count int := 0;
BEGIN
    -- Live users whose every membership is deleted are retired (unless they hold a platform role).
    UPDATE users us
       SET deleted = true, updated_at = now()
     WHERE NOT us.deleted
       AND EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = us.id)
       AND NOT EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = us.id AND NOT m.deleted)
       AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = us.id AND r.role = ANY (platform_roles));

    -- Same expression the unique index below uses.
    UPDATE users us
       SET email = lower(btrim(us.email))
     WHERE us.email IS NOT NULL AND us.email <> lower(btrim(us.email))
       AND EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = us.id AND NOT m.deleted);

    FOR cur_user IN
        SELECT us.id, us.public_id, us.username, us.email, us.status
          FROM users us
         WHERE NOT us.deleted
           AND EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = us.id AND NOT m.deleted)
         ORDER BY us.id
    LOOP
        has_platform := EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = cur_user.id AND r.role = ANY (platform_roles));
        kept := has_platform;
        IF has_platform THEN
            DELETE FROM user_roles WHERE user_id = cur_user.id AND role <> ALL (platform_roles);
        END IF;

        FOR cur_member IN
            SELECT om.id, om.organization_id, om.status, om.created_at
              FROM organization_members om
             WHERE om.user_id = cur_user.id AND NOT om.deleted
             ORDER BY om.created_at, om.id
        LOOP
            SELECT lower(o.code) INTO org_code FROM organizations o WHERE o.public_id = cur_member.organization_id;
            IF org_code IS NULL THEN
                RAISE NOTICE 'V49: skipped membership % of user %: organization % does not exist',
                    cur_member.id, cur_user.id, cur_member.organization_id;
                CONTINUE;
            END IF;
            new_status := CASE WHEN cur_user.status = 'SUSPENDED' OR cur_member.status = 'SUSPENDED' THEN 'SUSPENDED' ELSE 'ACTIVE' END;

            IF NOT kept THEN
                kept := true;
                UPDATE users SET organization_id = cur_member.organization_id, status = new_status, updated_at = now()
                 WHERE id = cur_user.id;
                DELETE FROM user_roles WHERE user_id = cur_user.id;
                INSERT INTO user_roles (user_id, role)
                SELECT cur_user.id, r.role FROM organization_member_roles r WHERE r.member_id = cur_member.id;
            ELSE
                candidate := NULL;
                FOR attempt IN 1..20 LOOP
                    SELECT org_code || '.' || string_agg(substr(alphabet, 1 + floor(random() * 31)::int, 1), '')
                      INTO candidate FROM generate_series(1, 8);
                    EXIT WHEN NOT EXISTS (SELECT 1 FROM users x WHERE x.username = candidate);
                    candidate := NULL;
                END LOOP;
                IF candidate IS NULL THEN
                    RAISE EXCEPTION 'V49: no unused username for organization % after 20 attempts', org_code;
                END IF;

                new_public := gen_random_uuid();
                INSERT INTO users (created_at, deleted, public_id, updated_at, class_name, credential_hash,
                                   date_of_birth, email, full_name, must_change_password, phone, status,
                                   student_code, username, organization_id)
                SELECT cur_member.created_at, false, new_public, now(), s.class_name, s.credential_hash,
                       s.date_of_birth, s.email, s.full_name, s.must_change_password, s.phone, new_status,
                       s.student_code, candidate, cur_member.organization_id
                  FROM users s WHERE s.id = cur_user.id
                RETURNING id INTO new_id;

                INSERT INTO user_roles (user_id, role)
                SELECT new_id, r.role FROM organization_member_roles r WHERE r.member_id = cur_member.id;

                INSERT INTO identity_account_split_log (original_user_id, original_public_id, new_user_id,
                                                        new_public_id, organization_id, old_username,
                                                        new_username, email)
                VALUES (cur_user.id, cur_user.public_id, new_id, new_public, cur_member.organization_id, cur_user.username, candidate, cur_user.email);

                FOR i IN 1..array_length(repoint, 1) LOOP
                    EXECUTE format('UPDATE %I SET %I = $1 WHERE %I = $2 AND %I = $3',
                                   repoint[i][1], repoint[i][2], repoint[i][2], repoint[i][3])
                       USING new_public, cur_user.public_id, cur_member.organization_id;
                END LOOP;
                split_count := split_count + 1;
            END IF;
        END LOOP;
    END LOOP;

    -- Everyone who was split logs in again; stale reset links must not reach the wrong organization.
    UPDATE refresh_tokens SET revoked = true
     WHERE user_id IN (SELECT original_user_id FROM identity_account_split_log);
    DELETE FROM password_reset_tokens
     WHERE user_id IN (SELECT original_user_id FROM identity_account_split_log);

    RAISE NOTICE 'V49: created % split accounts', split_count;

    -- Assertions: any failure rolls the whole migration back.
    SELECT count(*) INTO bad
      FROM organization_members m
      JOIN users s ON s.id = m.user_id
      JOIN organizations o ON o.public_id = m.organization_id
     WHERE NOT m.deleted AND NOT s.deleted
       AND (SELECT count(*)
              FROM users x
             WHERE x.organization_id = m.organization_id AND NOT x.deleted
               AND lower(btrim(x.email)) IS NOT DISTINCT FROM lower(btrim(s.email))
               AND (SELECT coalesce(array_agg(r.role ORDER BY r.role), '{}'::varchar[])
                      FROM user_roles r WHERE r.user_id = x.id)
                 = (SELECT coalesce(array_agg(r.role ORDER BY r.role), '{}'::varchar[])
                      FROM organization_member_roles r WHERE r.member_id = m.id)) <> 1;
    IF bad > 0 THEN
        RAISE EXCEPTION 'V49 assertion: % memberships without exactly one matching account', bad;
    END IF;

    SELECT count(*) INTO bad
      FROM users x
     WHERE x.organization_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = x.id AND r.role = ANY (platform_roles));
    IF bad > 0 THEN
        RAISE EXCEPTION 'V49 assertion: % accounts hold a platform role and an organization', bad;
    END IF;

    SELECT count(*) INTO bad FROM (
        SELECT organization_id, lower(btrim(email))
          FROM users
         WHERE organization_id IS NOT NULL AND NOT deleted AND email IS NOT NULL
         GROUP BY 1, 2 HAVING count(*) > 1) d;
    IF bad > 0 THEN
        RAISE EXCEPTION 'V49 assertion: % duplicate (organization, e-mail) pairs', bad;
    END IF;

    FOR i IN 1..array_length(repoint, 1) LOOP
        EXECUTE format('SELECT count(*) FROM %I t JOIN users x ON x.public_id = t.%I '
                       'WHERE t.%I IS NOT NULL AND x.organization_id IS NOT NULL AND t.%I <> x.organization_id',
                       repoint[i][1], repoint[i][2], repoint[i][3], repoint[i][3])
           INTO bad;
        IF bad > 0 THEN
            RAISE EXCEPTION 'V49 assertion: % rows of %.% belong to an account of another organization',
                bad, repoint[i][1], repoint[i][2];
        END IF;
    END LOOP;
END
$$;

-- One e-mail per organization (case-insensitive), for live organization accounts only.
CREATE UNIQUE INDEX uk_users_organization_email
    ON users (organization_id, lower(btrim(email)))
    WHERE deleted = false AND organization_id IS NOT NULL AND email IS NOT NULL;
