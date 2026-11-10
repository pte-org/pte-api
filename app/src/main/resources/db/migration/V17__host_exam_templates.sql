-- Host-owned exam templates. tenant_id NULL = platform default template (admin-managed);
-- a host template is cloned from a default and records it in base_definition_public_id, which
-- bounds the task types the host may keep. Existing rows are platform templates and stay NULL.
ALTER TABLE exam_definitions ADD COLUMN tenant_id uuid;
ALTER TABLE exam_definitions ADD COLUMN base_definition_public_id uuid;

-- "Exactly one ACTIVE" only holds for the platform default; hosts may keep several ACTIVE templates.
DROP INDEX ux_exam_definitions_single_active;
CREATE UNIQUE INDEX ux_exam_definitions_single_active ON exam_definitions USING btree (status)
    WHERE ((status)::text = 'ACTIVE'::text AND tenant_id IS NULL);

CREATE INDEX idx_exam_definitions_tenant ON exam_definitions USING btree (tenant_id) WHERE tenant_id IS NOT NULL;
