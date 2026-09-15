CREATE TABLE IF NOT EXISTS student_roster_entries (
    id BIGSERIAL PRIMARY KEY,
    student_public_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    student_code VARCHAR(255),
    phone VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_student_roster_tenant_student
        UNIQUE (tenant_id, student_public_id)
);

CREATE INDEX IF NOT EXISTS idx_student_roster_tenant_created_at
    ON student_roster_entries (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_student_roster_tenant_email
    ON student_roster_entries (tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_student_roster_tenant_full_name_lower
    ON student_roster_entries (tenant_id, LOWER(full_name));
CREATE INDEX IF NOT EXISTS idx_student_roster_tenant_phone
    ON student_roster_entries (tenant_id, phone);
CREATE INDEX IF NOT EXISTS idx_student_roster_tenant_student_code
    ON student_roster_entries (tenant_id, student_code);
