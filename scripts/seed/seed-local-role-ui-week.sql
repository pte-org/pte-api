-- Local UI fixtures only. Run through seed-local-role-ui-week.py.
-- Requires the existing full exam session named 'test' in organization e2e.
-- Atomic and repeatable: existing fixture sessions are kept, including UI edits.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(hashtext('pte-local-role-ui-week'));
CREATE TEMP TABLE ui_seed_config (password_hash text);
INSERT INTO ui_seed_config VALUES (:'password_hash');

-- Copy a fixture without assuming the order of columns or reusing identity IDs.
CREATE FUNCTION pg_temp.ui_copy(table_name regclass, source_id bigint, patch jsonb)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE columns_sql text; result_id bigint;
BEGIN
    SELECT string_agg(quote_ident(attname), ',' ORDER BY attnum) INTO columns_sql
    FROM pg_attribute WHERE attrelid = table_name AND attnum > 0
      AND NOT attisdropped AND attname <> 'id' AND attgenerated = '';
    EXECUTE format(
      'INSERT INTO %s (%s) SELECT %s FROM jsonb_populate_record(NULL::%s,
       (SELECT to_jsonb(s) FROM %s s WHERE id=$1) || $2) RETURNING id',
      table_name, columns_sql, columns_sql, table_name, table_name)
    INTO STRICT result_id USING source_id,
      jsonb_build_object('public_id',gen_random_uuid(),'created_at',now(),
                         'updated_at',now(),'deleted',false) || patch;
    RETURN result_id;
END $$;

DO $$
#variable_conflict use_variable
DECLARE
    tenant uuid; host_id uuid; proctor_id uuid; examiner_id uuid; student_id uuid;
    source_session exam_sessions%ROWTYPE; source_attempt exam_attempts%ROWTYPE;
    source_registration bigint; source_user bigint; source_subscription bigint;
    source_media bigint; media_id bigint; media_uuid uuid;
    session_id bigint; session_uuid uuid; subscription_id bigint; subscription_uuid uuid;
    attempt_id bigint; attempt_uuid uuid; answer_id bigint; answer_uuid uuid;
    assignment_id bigint; user_id bigint; member_id bigint; evaluation_id bigint;
    item record; fixture record; role_name text; user_name text; n integer; k integer;
    eligible integer; copy_patch jsonb; registration_id bigint;
BEGIN
    SELECT public_id INTO STRICT tenant FROM organizations WHERE code='e2e' AND NOT deleted;
    SELECT * INTO STRICT source_session FROM exam_sessions
      WHERE tenant_id=tenant AND name='test' AND NOT deleted;
    SELECT * INTO STRICT source_attempt FROM exam_attempts
      WHERE session_public_id=source_session.public_id AND NOT deleted ORDER BY id LIMIT 1;
    SELECT r.id INTO STRICT source_registration FROM exam_registrations r
      WHERE r.session_id=source_session.id AND NOT r.deleted ORDER BY r.id LIMIT 1;
    SELECT id INTO STRICT source_subscription FROM subscriptions
      WHERE public_id=source_session.subscription_id AND tenant_id=tenant;
    SELECT id INTO STRICT source_user FROM users
      WHERE public_id=source_attempt.student_public_id AND NOT deleted;
    SELECT id INTO STRICT source_media FROM media_objects
      WHERE status='UPLOADED' AND content_type LIKE 'audio/%' AND secure_url IS NOT NULL
        AND tenant_id IS NULL AND NOT deleted ORDER BY id LIMIT 1;
    SELECT public_id INTO STRICT host_id FROM users WHERE username='host.e2e@test.local';

    FOR n IN 1..15 LOOP
      role_name := CASE WHEN n=1 THEN 'PROCTOR' WHEN n=2 THEN 'EXAMINER' ELSE 'STUDENT' END;
      user_name := CASE WHEN n=1 THEN 'proctor.ui@test.local'
                       WHEN n=2 THEN 'examiner.ui@test.local'
                       ELSE 'student.ui.'||(n-2)||'@test.local' END;
      SELECT id INTO user_id FROM users WHERE username=user_name;
      IF user_id IS NULL THEN
        user_id := pg_temp.ui_copy('users',source_user,jsonb_build_object(
          'username',user_name,'email',user_name,'full_name','UI Week '||role_name||' '||n,
          'student_code',CASE WHEN n>2 THEN 'UI-WEEK-'||(n-2) ELSE NULL END,
          'class_name','UI Week Demo','phone',NULL,'date_of_birth',NULL,
          'credential_hash',(SELECT password_hash FROM ui_seed_config),
          'status','ACTIVE','must_change_password',false));
      ELSIF NOT EXISTS (SELECT 1 FROM organization_members m
          JOIN organization_member_roles r ON r.member_id=m.id
          WHERE m.user_id=user_id AND m.organization_id=tenant AND NOT m.deleted
            AND m.status='ACTIVE' AND r.role=role_name) THEN
        RAISE EXCEPTION 'Existing fixture username has unexpected membership: %',user_name;
      END IF;
      INSERT INTO organization_members(public_id,user_id,organization_id,status,deleted,created_at,updated_at)
        SELECT gen_random_uuid(),user_id,tenant,'ACTIVE',false,now(),now()
        WHERE NOT EXISTS(SELECT 1 FROM organization_members m WHERE m.user_id=user_id AND m.organization_id=tenant);
      SELECT m.id INTO STRICT member_id FROM organization_members m
        WHERE m.user_id=user_id AND m.organization_id=tenant;
      INSERT INTO organization_member_roles(member_id,role)
        SELECT member_id,role_name WHERE NOT EXISTS(
          SELECT 1 FROM organization_member_roles r WHERE r.member_id=member_id AND r.role=role_name);
    END LOOP;
    SELECT public_id INTO STRICT proctor_id FROM users WHERE username='proctor.ui@test.local';
    SELECT public_id INTO STRICT examiner_id FROM users WHERE username='examiner.ui@test.local';

    FOR k IN 1..3 LOOP
      IF EXISTS(SELECT 1 FROM exam_sessions WHERE session_code='UI-WEEK-'||k) THEN CONTINUE; END IF;
      subscription_id := pg_temp.ui_copy('subscriptions',source_subscription,jsonb_build_object(
        'license_key','UI-WEEK-SUB-'||k,'starts_at',now()-interval '1 day',
        'expires_at',now()+interval '10 days','status','ACTIVE','max_students_per_session',30));
      SELECT public_id INTO STRICT subscription_uuid FROM subscriptions WHERE id=subscription_id;
      copy_patch := jsonb_build_object(
        'session_code','UI-WEEK-'||k,'name',CASE k
          WHEN 1 THEN 'UI Week - Live monitoring and attendance'
          WHEN 2 THEN 'UI Week - Upcoming exam'
          ELSE 'UI Week - Examiner marking workspace' END,
        'capacity',30,'subscription_id',subscription_uuid,'license_key','UI-WEEK-SUB-'||k,
        'opens_at',CASE WHEN k=2 THEN now()+interval '1 day' ELSE now()-interval '1 hour' END,
        'closes_at',now()+interval '7 days',
        'status',CASE WHEN k=2 THEN 'SCHEDULED' ELSE 'OPEN' END,
        'grading_status',CASE WHEN k=3 THEN 'FROZEN' ELSE NULL END,
        'grading_marking_mode',CASE WHEN k=3 THEN 'MANUAL_EXAMINER' ELSE NULL END,
        'grading_frozen_at',CASE WHEN k=3 THEN now() ELSE NULL END,
        'grading_frozen_by_public_id',CASE WHEN k=3 THEN host_id ELSE NULL END,
        'grading_completed_at',NULL,'grading_preview_version',NULL,
        'publication_locked_at',NULL,'publication_public_id',NULL,
        'proctor_required',true,'lockdown_mode','STRICT','device_check_required',false);
      -- Marking uses its own closed session so the cohort remains stable for a week.
      IF k=3 THEN copy_patch := copy_patch || jsonb_build_object(
        'status','CLOSED','opens_at',now()-interval '4 hours','closes_at',now()-interval '1 hour'); END IF;
      session_id := pg_temp.ui_copy('exam_sessions',source_session.id,copy_patch);
      SELECT public_id INTO STRICT session_uuid FROM exam_sessions WHERE id=session_id;
      INSERT INTO exam_session_selected_skills SELECT session_id,s.skill
        FROM exam_session_selected_skills s WHERE s.session_id=source_session.id;
      INSERT INTO exam_session_proctors(public_id,created_at,updated_at,deleted,
        proctor_public_id,role,tenant_id,session_id)
        VALUES(gen_random_uuid(),now(),now(),false,proctor_id,'LEAD_PROCTOR',tenant,session_id);

      FOR n IN 1..CASE WHEN k=3 THEN 4 WHEN k=2 THEN 3 ELSE 6 END LOOP
        SELECT public_id INTO STRICT student_id FROM users
          WHERE username='student.ui.'||(CASE k WHEN 1 THEN n WHEN 2 THEN n+6 ELSE n+9 END)||'@test.local';
        registration_id := pg_temp.ui_copy('exam_registrations',source_registration,jsonb_build_object(
          'student_public_id',student_id,'session_id',session_id,'license_key','UI-WEEK-SUB-'||k,
          'status','ENROLLED','attendance_status',CASE WHEN k<>1 OR n=1 THEN 'UNMARKED'
            WHEN n=2 THEN 'PRESENT' WHEN n=3 THEN 'LATE' WHEN n=4 THEN 'ABSENT' ELSE 'UNMARKED' END,
          'identity_status',CASE WHEN k=1 AND n=2 THEN 'VERIFIED'
            WHEN k=1 AND n=3 THEN 'FAILED' ELSE 'UNVERIFIED' END,
          'attendance_version',0,'last_action_at',NULL,'last_actor_public_id',NULL,
          'last_actor_role',NULL,'last_reason_or_note',NULL,'identity_note',NULL,
          'prior_session_public_id',NULL,'prior_session_name',NULL,'prior_status',NULL));
        IF k=2 OR (k=1 AND n>3) THEN CONTINUE; END IF;
        attempt_uuid := gen_random_uuid();
        attempt_id := pg_temp.ui_copy('exam_attempts',source_attempt.id,jsonb_build_object(
          'public_id',attempt_uuid,'session_public_id',session_uuid,'student_public_id',student_id,
          'status',CASE WHEN k=3 THEN 'SUBMITTED' ELSE 'IN_PROGRESS' END,
          'started_at',now()-interval '20 minutes','last_seen_at',now(),
          'exam_end_time',CASE WHEN k=3 THEN now()-interval '1 hour' ELSE now()+interval '7 days' END,
          'submitted_at',CASE WHEN k=3 THEN now()-interval '10 minutes' ELSE NULL END,
          'grading_excluded',false,'grading_exclusion_reason',NULL,'version',0));
        IF k<>3 THEN CONTINUE; END IF;
        media_id := pg_temp.ui_copy('media_objects',source_media,jsonb_build_object(
          'owner_public_id',student_id,'tenant_id',tenant,'purpose','SUBMISSION_AUDIO',
          'storage_key','ui-week/'||attempt_uuid,'asset_id',NULL));
        SELECT public_id INTO STRICT media_uuid FROM media_objects WHERE id=media_id;
        eligible := 0;
        -- One answer per AI-scored task type covers speaking and writing editors.
        FOR item IN SELECT DISTINCT ON (i.pte_task_type) i.* FROM exam_version_items i
          JOIN exam_versions v ON v.id=i.exam_version_id
          WHERE v.public_id=source_session.exam_version_public_id AND i.pte_task_type IN (
            'READ_ALOUD','REPEAT_SENTENCE','DESCRIBE_IMAGE','RE_TELL_LECTURE',
            'ANSWER_SHORT_QUESTION','RESPOND_TO_A_SITUATION','SUMMARIZE_GROUP_DISCUSSION',
            'WRITE_ESSAY','SUMMARIZE_WRITTEN_TEXT','SUMMARIZE_SPOKEN_TEXT')
          ORDER BY i.pte_task_type,i.order_index LOOP
          answer_uuid := gen_random_uuid(); eligible := eligible+1;
          INSERT INTO attempt_answers(public_id,created_at,updated_at,deleted,expired,
            item_order_index,item_public_id,payload,status,attempt_id)
            VALUES(answer_uuid,now(),now(),false,false,item.order_index,item.public_id,
              CASE WHEN item.section='SPEAKING' THEN media_uuid::text ELSE
              'Education supports independent thinking and gives people practical skills for their future careers. Students benefit from clear feedback, reliable resources and regular opportunities to practise. Technology can make these resources more accessible, while teachers provide guidance and encourage thoughtful discussion. A balanced approach combines classroom interaction with individual study, allowing learners to develop confidence and improve their performance over time. This response is synthetic local UI test data.' END,
              'SCORED',attempt_id) RETURNING id INTO answer_id;
          INSERT INTO answer_evaluations(public_id,created_at,updated_at,deleted,
            answer_public_id,attempt_public_id,lock_version,score,scored_at,
            session_public_id,source,tenant_id,dispatch_token,fallback,
            exam_definition_public_id,item_public_id,scoring_method,section,status,task_type,
            ai_provider,ai_provider_category,ai_model,feedback)
            VALUES(gen_random_uuid(),now(),now(),false,answer_uuid,attempt_uuid,0,65,now(),
              session_uuid,'AI',tenant,0,false,source_session.exam_definition_public_id,
              item.public_id,CASE WHEN item.section='SPEAKING' THEN 'AI_SPEECH' ELSE 'AI_TEXT' END,
              item.section,'SCORED',item.pte_task_type,'ui-fixture','STUB','ui-fixture',
              'Synthetic score for local UI testing; not a real assessment.');
        END LOOP;
        IF eligible=0 THEN RAISE EXCEPTION 'No AI-eligible items in source exam'; END IF;
        INSERT INTO grading_assignments(public_id,created_at,updated_at,deleted,
          assigned_at,assigned_by_public_id,attempt_public_id,eligible_answer_count,
          examiner_public_id,session_public_id,status,tenant_id,work_version)
          VALUES(gen_random_uuid(),now(),now(),false,now(),host_id,attempt_uuid,eligible,
            examiner_id,session_uuid,'PENDING',tenant,0);
      END LOOP;
    END LOOP;
END $$;
COMMIT;
SELECT name,session_code,status,opens_at,closes_at,public_id
FROM exam_sessions WHERE session_code LIKE 'UI-WEEK-%' ORDER BY session_code;
