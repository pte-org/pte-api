-- Local UI-WEEK fixtures only. Synthetic evidence; never run on production.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(hashtext('pte-local-role-ui-week'));
DO $$
DECLARE
  exam exam_sessions%ROWTYPE;
  actor uuid;
  student_id uuid;
  attempt_id uuid;
  ordinal integer;
  event_origin text;
  event_type text;
  event_title text;
  event_time timestamptz;
  attempt_count integer;
BEGIN
  SELECT public_id INTO STRICT actor FROM users
    WHERE username='proctor.ui@test.local' AND NOT deleted;
  FOR exam IN SELECT s.* FROM exam_sessions s
    JOIN organizations o ON o.public_id=s.tenant_id
    WHERE o.code='e2e' AND s.session_code IN ('UI-WEEK-1','UI-WEEK-3') AND NOT s.deleted
  LOOP
    IF NOT EXISTS (SELECT 1 FROM organization_members m JOIN users u ON u.id=m.user_id
        JOIN organization_member_roles r ON r.member_id=m.id
        WHERE u.public_id=actor AND m.organization_id=exam.tenant_id AND r.role='PROCTOR') THEN
      RAISE EXCEPTION 'Fixture proctor must belong to the fixture tenant';
    END IF;
    SELECT count(*) INTO attempt_count FROM exam_attempts a
      JOIN users u ON u.public_id=a.student_public_id
      WHERE a.session_public_id=exam.public_id AND NOT a.deleted
        AND u.username LIKE 'student.ui.%@test.local';
    IF attempt_count=0 THEN RAISE EXCEPTION 'Fixture needs at least one attempt'; END IF;
    FOR ordinal IN 1..34 LOOP
      SELECT a.student_public_id,a.public_id INTO STRICT student_id,attempt_id FROM exam_attempts a
        JOIN exam_registrations r ON r.student_public_id=a.student_public_id AND r.session_id=exam.id
        JOIN users u ON u.public_id=r.student_public_id
        WHERE a.session_public_id=exam.public_id AND NOT a.deleted AND NOT r.deleted
          AND u.username LIKE 'student.ui.%@test.local'
        ORDER BY u.username,a.id OFFSET ((ordinal-1)%attempt_count) LIMIT 1;
      event_origin := CASE WHEN ordinal<=6 THEN 'PROCTOR_FLAG' ELSE 'AUTO' END;
      event_type := CASE WHEN ordinal<=6 THEN
        (ARRAY['TAB_SWITCH','MULTIPLE_FACES','FACE_NOT_VISIBLE','SUSPICIOUS_AUDIO','TECHNICAL_ISSUE','OTHER'])[ordinal]
        ELSE (ARRAY['LOCKDOWN_FULLSCREEN_EXIT','LOCKDOWN_SHORTCUT_BLOCKED','LOCKDOWN_CLIPBOARD_PASTE'])[(ordinal%3)+1] END;
      event_title := 'UI Audit Demo - '||event_origin||' - '||lpad(ordinal::text,2,'0');
      IF EXISTS (SELECT 1 FROM exam_incidents WHERE tenant_id=exam.tenant_id
          AND session_public_id=exam.public_id AND title=event_title) THEN CONTINUE; END IF;
      -- Keep synthetic timestamps within each attempt's actual interval.
      SELECT started_at + (LEAST(COALESCE(submitted_at,now()),now())-started_at)
        * ((ordinal-1)::double precision/34) INTO event_time
        FROM exam_attempts WHERE public_id=attempt_id;
      INSERT INTO exam_incidents(public_id,created_at,updated_at,deleted,tenant_id,
        session_public_id,student_public_id,attempt_public_id,origin,type,title,description,
        severity,status,reporter_role,reporter_name,reporter_public_id,current_owner_role,
        client_event_id,client_occurred_at,detected_at)
      VALUES(gen_random_uuid(),event_time,event_time,false,exam.tenant_id,exam.public_id,
        student_id,attempt_id,event_origin,event_type,event_title,
        'Dữ liệu mẫu UI: '||event_type||'. Sự kiện giả lập để kiểm thử nhật ký, không phải bằng chứng vi phạm thực tế.',
        CASE WHEN event_origin='AUTO' THEN CASE WHEN ordinal%3=0 THEN 'CRITICAL' ELSE 'WARNING' END
          WHEN ordinal%3=0 THEN 'HIGH' WHEN ordinal%3=1 THEN 'LOW' ELSE 'MEDIUM' END,
        'NEW',CASE WHEN event_origin='PROCTOR_FLAG' THEN 'PROCTOR' ELSE 'STUDENT' END,
        CASE WHEN event_origin='PROCTOR_FLAG' THEN 'Giám thị UI Demo' ELSE 'Thí sinh UI Demo' END,
        CASE WHEN event_origin='PROCTOR_FLAG' THEN actor ELSE student_id END,'HOST_ADMIN',
        CASE WHEN event_origin='AUTO' THEN 'ui-audit-demo-'||ordinal END,
        CASE WHEN event_origin='AUTO' THEN event_time END,event_time);
    END LOOP;
  END LOOP;
END $$;
COMMIT;
SELECT s.session_code,i.origin,count(*) FROM exam_incidents i
JOIN exam_sessions s ON s.public_id=i.session_public_id
WHERE i.title LIKE 'UI Audit Demo - %' AND NOT i.deleted
GROUP BY s.session_code,i.origin ORDER BY s.session_code,i.origin;
