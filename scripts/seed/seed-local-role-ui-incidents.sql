-- Local-only companion to seed-local-role-ui-week.sql.
-- Uses the dedicated UI-WEEK-3 fixture; never changes its CLOSED state.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(hashtext('pte-local-role-ui-week'));
DO $$
DECLARE
  exam exam_sessions%ROWTYPE;
  actor uuid;
  registration record;
  scenario record;
  incident_id uuid;
  student_id uuid;
  attempt_id uuid;
  target_status text;
  snapshot jsonb;
BEGIN
  SELECT s.* INTO STRICT exam FROM exam_sessions s
    JOIN organizations o ON o.public_id=s.tenant_id
    WHERE s.session_code='UI-WEEK-3' AND o.code='e2e' AND NOT s.deleted;
  SELECT public_id INTO STRICT actor FROM users WHERE username='proctor.ui@test.local' AND NOT deleted;
  IF NOT EXISTS (SELECT 1 FROM organization_members m JOIN users u ON u.id=m.user_id
      JOIN organization_member_roles r ON r.member_id=m.id
      WHERE u.public_id=actor AND m.organization_id=exam.tenant_id AND r.role='PROCTOR') THEN
    RAISE EXCEPTION 'Fixture proctor is not a member of the fixture tenant';
  END IF;

  FOR registration IN SELECT r.*,row_number() OVER (ORDER BY u.username) ordinal
      FROM exam_registrations r JOIN users u ON u.public_id=r.student_public_id
      WHERE r.session_id=exam.id AND u.username LIKE 'student.ui.%@test.local' AND NOT r.deleted
      ORDER BY u.username LOOP
    -- Keep any attendance the operator has already edited.
    IF registration.attendance_version<>0 OR registration.attendance_status<>'UNMARKED' THEN CONTINUE; END IF;
    target_status := (ARRAY['PRESENT','LATE','ABSENT','UNMARKED'])[registration.ordinal];
    IF target_status IS NULL OR target_status='UNMARKED' THEN CONTINUE; END IF;
    UPDATE exam_registrations SET attendance_status=target_status,attendance_version=1,
      identity_status=CASE WHEN target_status='PRESENT' THEN 'VERIFIED'
        WHEN target_status='LATE' THEN 'FAILED' ELSE 'UNVERIFIED' END,
      identity_note=CASE WHEN target_status='LATE' THEN 'Dữ liệu mẫu: cần kiểm tra lại giấy tờ.' ELSE NULL END,
      last_action_at=now(),last_actor_public_id=actor,last_actor_role='PROCTOR',
      last_reason_or_note='UI seed: dữ liệu điểm danh mẫu cho ca đã kết thúc.',updated_at=now()
      WHERE id=registration.id;
    snapshot := jsonb_build_object('status',target_status,'version',1,'actorRole','PROCTOR',
      'reasonOrNote','UI seed: dữ liệu điểm danh mẫu cho ca đã kết thúc.',
      'studentPublicId',registration.student_public_id,'registrationPublicId',registration.public_id);
    INSERT INTO audit_logs(public_id,created_at,updated_at,deleted,action,actor_role,actor_user_id,
      aggregate_id,aggregate_type,before_state,after_state,request_public_id,summary,tenant_id)
      VALUES(gen_random_uuid(),now(),now(),false,'ATTENDANCE_RECORDED','PROCTOR',actor,
        exam.public_id||':'||registration.student_public_id,'SESSION_ATTENDANCE',
        snapshot||jsonb_build_object('status','UNMARKED','version',0),snapshot,gen_random_uuid(),
        'UI seed: Attendance UNMARKED -> '||target_status,exam.tenant_id);
  END LOOP;

  FOR scenario IN SELECT * FROM (VALUES
    (1,'UI Demo - Tai nghe không phát âm thanh','TECHNICAL_ISSUE','MEDIUM','NEW'),
    (2,'UI Demo - Kiểm tra giấy tờ thí sinh','IDENTITY_VERIFICATION','HIGH','IN_PROGRESS'),
    (3,'UI Demo - Nghi vấn trao đổi trong ca thi','RULE_VIOLATION','HIGH','ESCALATED'),
    (4,'UI Demo - Đã xử lý kết nối mạng gián đoạn','TECHNICAL_ISSUE','MEDIUM','RESOLVED'),
    (5,'UI Demo - Đã đóng phản ánh tiếng ồn','DISRUPTION','LOW','CLOSED'),
    (6,'UI Demo - Ghi nhận thí sinh đến muộn','OTHER','LOW','NEW')
  ) AS cases(ordinal,title,category,severity,status) LOOP
    IF EXISTS(SELECT 1 FROM exam_incidents WHERE session_public_id=exam.public_id
      AND title=scenario.title AND NOT deleted) THEN CONTINUE; END IF;
    SELECT r.student_public_id INTO student_id FROM exam_registrations r
      JOIN users u ON u.public_id=r.student_public_id
      WHERE r.session_id=exam.id AND u.username LIKE 'student.ui.%@test.local' AND NOT r.deleted
      ORDER BY u.username OFFSET ((scenario.ordinal-1)%4) LIMIT 1;
    SELECT public_id INTO attempt_id FROM exam_attempts WHERE session_public_id=exam.public_id
      AND student_public_id=student_id AND NOT deleted ORDER BY id DESC LIMIT 1;
    incident_id := gen_random_uuid();
    INSERT INTO exam_incidents(public_id,created_at,updated_at,deleted,attempt_public_id,
      current_owner_name,current_owner_role,description,detected_at,origin,
      reporter_name,reporter_public_id,reporter_role,resolution_summary,
      session_public_id,severity,status,student_public_id,tenant_id,title,type)
      VALUES(incident_id,now()-interval '1 hour',now(),false,attempt_id,
        CASE WHEN scenario.status='ESCALATED' THEN 'Quản trị nền tảng mẫu' ELSE 'Host phụ trách ca thi' END,
        CASE WHEN scenario.status='ESCALATED' THEN 'PLATFORM_ADMIN' ELSE 'HOST_ADMIN' END,
        'Dữ liệu mẫu để kiểm thử giao diện. Có thể mở chi tiết và thử các bộ lọc.',now()-interval '1 hour',
        'REPORTED','Giám thị UI Demo',actor,'PROCTOR',
        CASE WHEN scenario.status IN ('RESOLVED','CLOSED') THEN 'Đã kiểm tra và xử lý trong dữ liệu mẫu.' ELSE NULL END,
        exam.public_id,scenario.severity,scenario.status,student_id,exam.tenant_id,scenario.title,scenario.category);
    INSERT INTO audit_logs(public_id,created_at,updated_at,deleted,action,actor_role,actor_user_id,
      aggregate_id,aggregate_type,after_state,request_public_id,summary,tenant_id)
      VALUES(gen_random_uuid(),now(),now(),false,'INCIDENT_CHANGED','PROCTOR',actor,
        incident_id::text,'EXAM_INCIDENT',jsonb_build_object('action','CREATE','fromStatus',NULL,
        'toStatus',scenario.status,'actorRole','PROCTOR','actorName','Giám thị UI Demo',
        'notes','UI seed: dữ liệu mẫu ở nhiều trạng thái.'),gen_random_uuid(),
        'UI seed: CREATE -> '||scenario.status,exam.tenant_id);
  END LOOP;
END $$;
COMMIT;
SELECT status,count(*) FROM exam_incidents
WHERE session_public_id=(SELECT public_id FROM exam_sessions WHERE session_code='UI-WEEK-3')
GROUP BY status ORDER BY status;
