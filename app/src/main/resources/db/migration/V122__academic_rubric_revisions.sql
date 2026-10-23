CREATE TABLE IF NOT EXISTS rubric_revisions (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    rubric_key VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    task_type_key VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'PRACTICE',
    version INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    author_user_public_id UUID,
    reviewer_user_public_id UUID,
    publisher_user_public_id UUID,
    change_reason VARCHAR(1000),
    rejection_reason VARCHAR(1000),
    document_json TEXT NOT NULL,
    references_json TEXT,
    anchors_json TEXT,
    canonical_hash VARCHAR(64),
    runtime_compatibility_hash VARCHAR(64),
    submitted_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rubric_revision_key_version UNIQUE (rubric_key, version)
);

CREATE TABLE IF NOT EXISTS rubric_criteria (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    revision_id BIGINT NOT NULL REFERENCES rubric_revisions(id),
    criterion_id VARCHAR(64) NOT NULL,
    sequence INTEGER NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    applicability VARCHAR(32) NOT NULL,
    evidence_policy_json TEXT NOT NULL,
    CONSTRAINT uk_rubric_criterion_key UNIQUE (revision_id, criterion_id)
);

CREATE TABLE IF NOT EXISTS rubric_criterion_levels (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    criterion_id BIGINT NOT NULL REFERENCES rubric_criteria(id),
    level_key VARCHAR(32) NOT NULL,
    level_order INTEGER NOT NULL,
    label_vi VARCHAR(128) NOT NULL,
    label_en VARCHAR(128) NOT NULL,
    descriptor TEXT NOT NULL,
    CONSTRAINT uk_rubric_level_key UNIQUE (criterion_id, level_key)
);

CREATE INDEX IF NOT EXISTS idx_rubric_revision_task_status
    ON rubric_revisions (task_type_key, channel, status)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rubric_revision_author
    ON rubric_revisions (author_user_public_id)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_rubric_published_task_channel
    ON rubric_revisions (task_type_key, channel)
    WHERE deleted = FALSE AND status = 'PUBLISHED';

ALTER TABLE practice_session_items
    ADD COLUMN IF NOT EXISTS rubric_revision_public_id UUID,
    ADD COLUMN IF NOT EXISTS rubric_revision_number INTEGER,
    ADD COLUMN IF NOT EXISTS rubric_canonical_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS rubric_snapshot_json TEXT,
    ADD COLUMN IF NOT EXISTS runtime_compatibility_hash VARCHAR(64);

ALTER TABLE practice_explanations
    ADD COLUMN IF NOT EXISTS rubric_revision_public_id UUID,
    ADD COLUMN IF NOT EXISTS rubric_canonical_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS rubric_provenance_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_practice_session_items_rubric
    ON practice_session_items (rubric_revision_public_id);

CREATE INDEX IF NOT EXISTS idx_practice_explanations_rubric
    ON practice_explanations (rubric_revision_public_id);
