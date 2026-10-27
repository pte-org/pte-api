-- Practice timed / untimed modes (plan 261024-hung-practice-timed-modes, phase 1).
-- A session records the mode and question count the student chose; there is no session-wide time limit any more,
-- and each item can pin the per-task timing of the active Score Template (filled when a TIMED session begins).

ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS timing_mode VARCHAR(16);
ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS question_count INTEGER;
ALTER TABLE practice_sessions ALTER COLUMN time_limit_seconds SET DEFAULT 0;

ALTER TABLE practice_sessions ADD CONSTRAINT ck_practice_sessions_timing_mode
    CHECK (timing_mode IS NULL OR timing_mode IN ('TIMED', 'UNTIMED'));
ALTER TABLE practice_sessions ADD CONSTRAINT ck_practice_sessions_question_count
    CHECK (question_count IS NULL OR question_count BETWEEN 1 AND 50);

-- pre_record_seconds is the template's gap before recording, not the exam's PinnedItem.prepSeconds
-- (which adds preListen and prompt audio length).
ALTER TABLE practice_responses ADD COLUMN IF NOT EXISTS pre_listen_seconds INTEGER;
ALTER TABLE practice_responses ADD COLUMN IF NOT EXISTS pre_record_seconds INTEGER;
ALTER TABLE practice_responses ADD COLUMN IF NOT EXISTS response_seconds INTEGER;

-- Unfinished sessions created under the old 60-minute limit would expire as soon as they are opened.
-- Null deadline means "never expires"; EXPIRED, COMPLETED and DISCARDED sessions keep their history.
UPDATE practice_sessions SET deadline_at = NULL
WHERE status IN ('OVERVIEW', 'IN_PROGRESS') AND deadline_at IS NOT NULL;

-- Rollback policy: columns are additive; restoring deadlines is not required (practice is unstaked).
