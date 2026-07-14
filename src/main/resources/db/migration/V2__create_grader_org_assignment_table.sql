-- Schema reference: grader_org_assignment join table (Grader ↔ Organization many-to-many)
-- grader_id is BIGINT (FK to grader.id — BaseEntity Long PK)
CREATE TABLE IF NOT EXISTS grader_org_assignment (
    grader_id       BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (grader_id, organization_id),
    CONSTRAINT fk_grader_org_grader       FOREIGN KEY (grader_id)       REFERENCES grader(id)       ON DELETE CASCADE,
    CONSTRAINT fk_grader_org_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_grader_org_assignment_grader_id ON grader_org_assignment(grader_id);
CREATE INDEX IF NOT EXISTS idx_grader_org_assignment_org_id    ON grader_org_assignment(organization_id);
