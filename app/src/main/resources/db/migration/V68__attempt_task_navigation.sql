ALTER TABLE pinned_exam_snapshots
    ADD COLUMN IF NOT EXISTS exam_mode VARCHAR(16);

UPDATE pinned_exam_snapshots AS pinned
SET exam_mode = COALESCE(exam_session.exam_mode, 'MOCK_TEST')
FROM exam_sessions AS exam_session
WHERE exam_session.public_id = pinned.source_session_public_id
  AND pinned.exam_mode IS NULL;

UPDATE pinned_exam_snapshots
SET exam_mode = 'MOCK_TEST'
WHERE exam_mode IS NULL;

ALTER TABLE pinned_exam_snapshots
    ALTER COLUMN exam_mode SET NOT NULL;
