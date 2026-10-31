-- Local-only repair for Quang's E2E-261029-REVD preview session.
-- Keep the published snapshot immutable: clone its content and rebind only
-- this unused READY session. The old snapshot remains available for rollback.
\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE exam_sessions IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE exam_attempts IN SHARE MODE;

DO $repair$
DECLARE
    target_session exam_sessions%ROWTYPE;
    old_snapshot exam_versions%ROWTYPE;
    new_snapshot exam_versions%ROWTYPE;
    next_version integer;
    expected_count integer;
    cloned_count integer;
BEGIN
    SELECT * INTO STRICT target_session FROM exam_sessions
    WHERE public_id = '73a6082b-5a38-4662-9bf2-93dc67cab332'
      AND session_code = 'E2E-261029-REVD'
      AND tenant_id = '59bd919d-8dd7-4cbf-99fe-81cb8c2d7015'
      AND status = 'READY' AND NOT deleted
      AND exam_version_public_id = '111c0683-7536-4cf0-93b8-78e7e418acb6';
    IF EXISTS (SELECT 1 FROM exam_attempts
               WHERE session_public_id = target_session.public_id
                  OR exam_version_public_id = target_session.exam_version_public_id)
       OR EXISTS (SELECT 1 FROM exam_results WHERE snapshot_public_id = target_session.exam_version_public_id) THEN
        RAISE EXCEPTION 'Refusing to rebind a snapshot with student attempts or results';
    END IF;
    SELECT * INTO STRICT old_snapshot FROM exam_versions
    WHERE public_id = target_session.exam_version_public_id AND tenant_id = target_session.tenant_id;
    SELECT COUNT(*) INTO expected_count FROM exam_version_items WHERE exam_version_id = old_snapshot.id;
    IF expected_count <> 75 THEN
        RAISE EXCEPTION 'Expected exactly 75 original questions, found %', expected_count;
    END IF;
    SELECT COALESCE(MAX(version), 0) + 1 INTO next_version FROM exam_versions
    WHERE source_blueprint_public_id = old_snapshot.source_blueprint_public_id;
    INSERT INTO exam_versions
    SELECT (jsonb_populate_record(NULL::exam_versions, to_jsonb(old_snapshot) || jsonb_build_object(
        'id', nextval(pg_get_serial_sequence('exam_versions', 'id')),
        'public_id', gen_random_uuid(), 'version', next_version,
        'created_at', CURRENT_TIMESTAMP, 'updated_at', CURRENT_TIMESTAMP,
        'generation_algorithm_version', 'PTE_PART_ORDER_REPAIR_V2'))).*
    RETURNING * INTO new_snapshot;

    INSERT INTO exam_version_items
    SELECT (jsonb_populate_record(NULL::exam_version_items, to_jsonb(item) || jsonb_build_object(
        'exam_version_id', new_snapshot.id, 'public_id', gen_random_uuid(),
        'order_index', (row_number() OVER (ORDER BY CASE item.section
            WHEN 'SPEAKING' THEN 0 WHEN 'WRITING' THEN 1 WHEN 'READING' THEN 2 WHEN 'LISTENING' THEN 3 END,
            CASE WHEN item.section = 'LISTENING' AND COALESCE(item.task_type_code, item.pte_task_type) = 'SUMMARIZE_SPOKEN_TEXT'
                 THEN 0 ELSE 1 END,
            item.order_index) - 1)::integer))).*
    FROM exam_version_items item WHERE item.exam_version_id = old_snapshot.id;
    GET DIAGNOSTICS cloned_count = ROW_COUNT;
    IF cloned_count <> expected_count OR EXISTS (
        (SELECT to_jsonb(item) - ARRAY['exam_version_id', 'public_id', 'order_index']
         FROM exam_version_items item WHERE exam_version_id = old_snapshot.id)
        EXCEPT ALL
        (SELECT to_jsonb(item) - ARRAY['exam_version_id', 'public_id', 'order_index']
         FROM exam_version_items item WHERE exam_version_id = new_snapshot.id)
    ) THEN
        RAISE EXCEPTION 'Snapshot clone failed content preservation checks';
    END IF;
    UPDATE exam_sessions SET exam_version_public_id = new_snapshot.public_id, updated_at = CURRENT_TIMESTAMP
    WHERE id = target_session.id AND tenant_id = target_session.tenant_id;
    RAISE NOTICE 'Rebound E2E-261029-REVD: old snapshot %, new snapshot %, questions %',
        old_snapshot.public_id, new_snapshot.public_id, cloned_count;
END
$repair$;
COMMIT;
