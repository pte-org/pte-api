-- Exam attempt status model + heartbeat, foundation for realtime proctor updates.
ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'SECTION_SWITCH', 'SUBMITTED', 'DISCONNECTED'));
ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ;

-- The timeout scan filters by status IN (...) AND last_heartbeat_at < cutoff.
CREATE INDEX IF NOT EXISTS idx_exam_attempt_status_heartbeat ON exam_attempt(status, last_heartbeat_at);
