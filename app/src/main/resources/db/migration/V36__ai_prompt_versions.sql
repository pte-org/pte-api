-- Editable AI prompts: one row per version of one prompt, content stored as JSON (title, intro blocks, sections).
-- Additive only. Lifecycle (DRAFT -> PENDING -> ACTIVE | REJECTED, old ACTIVE -> RETIRED) mirrors scoring_rubric_versions,
-- but the rows are separate: rubric versions are pinned by exam definitions and carry typed numeric parameters.
CREATE TABLE ai_prompt_versions (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('GENERATOR', 'REVIEWER', 'SCORER', 'SHARED')),
    prompt_key VARCHAR(64) NOT NULL,
    version_no INT NOT NULL CHECK (version_no >= 1),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'REJECTED', 'RETIRED')),
    content JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    author_user_public_id UUID,
    updated_by_user_public_id UUID,
    approver_user_public_id UUID,
    rejection_reason VARCHAR(1000),
    submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ai_prompt_versions_key_version UNIQUE (kind, prompt_key, version_no)
);
-- At most one live version per prompt, enforced by the database so two concurrent activations cannot both win.
CREATE UNIQUE INDEX uk_ai_prompt_versions_one_active
    ON ai_prompt_versions (kind, prompt_key)
    WHERE status = 'ACTIVE' AND deleted = FALSE;
CREATE INDEX ix_ai_prompt_versions_key_status ON ai_prompt_versions (kind, prompt_key, status);
COMMENT ON COLUMN ai_prompt_versions.content IS 'PromptContent JSON: title, intro blocks, sections of blocks (TEXT, LIST, CODE, LEVELS)';
COMMENT ON COLUMN ai_prompt_versions.content_hash IS 'SHA-256 of the canonical content JSON; a prompt test is valid only for the hash it ran on';
