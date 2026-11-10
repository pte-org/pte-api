-- A prompt version is sent for approval only after a test passed on its exact content (PLATFORM_ADMIN may skip the test).
-- The test result is tied to the content hash it ran on, so editing the content makes an older result stale.
ALTER TABLE ai_prompt_versions ADD COLUMN test_passed BOOLEAN;
ALTER TABLE ai_prompt_versions ADD COLUMN tested_content_hash VARCHAR(64);
ALTER TABLE ai_prompt_versions ADD COLUMN test_note VARCHAR(500);
ALTER TABLE ai_prompt_versions ADD COLUMN tested_by_user_public_id UUID;
ALTER TABLE ai_prompt_versions ADD COLUMN tested_at TIMESTAMPTZ;
-- One draft or pending version per prompt at a time, so two editors cannot fork a prompt unnoticed.
CREATE UNIQUE INDEX uk_ai_prompt_versions_one_open
    ON ai_prompt_versions (kind, prompt_key)
    WHERE status IN ('DRAFT', 'PENDING') AND deleted = FALSE;
