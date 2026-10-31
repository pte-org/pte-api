-- Student chat consent (plan 261028-ninh-practice-ai-chat-box, privacy hand-off).
-- One row per student: the policy version the student agreed to and when. Chat stays closed until the row holds
-- the current version, so changing the policy text (and ChatConstants.CONSENT_POLICY_VERSION) asks everyone again.
-- student_id is the student's public UUID (users.public_id), as in the other chat tables.
CREATE TABLE chat_consent (
    student_id UUID PRIMARY KEY,
    policy_version VARCHAR(32) NOT NULL,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- The student confirmed being at least 16, or having a parent or guardian's consent.
    age_confirmed BOOLEAN NOT NULL
);

-- The nightly retention job looks for messages older than the retention window.
CREATE INDEX idx_chat_message_created ON chat_message (created_at);

-- ... and, for each old session, for any message that still points to it. The existing session index starts with
-- student_id, so it cannot answer "any message in this session?" without scanning the table.
CREATE INDEX idx_chat_message_session_id ON chat_message (session_id);
