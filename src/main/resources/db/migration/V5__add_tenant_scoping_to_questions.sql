-- Dual-track question authoring: Vendor-shared (tenant_id NULL) vs Host-scoped content.
ALTER TABLE questions ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'VENDOR'
    CHECK (source IN ('VENDOR', 'HOST'));
ALTER TABLE questions ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_questions_tenant_id ON questions(tenant_id);
