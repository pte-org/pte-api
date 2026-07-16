CREATE TABLE IF NOT EXISTS proctor (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    organization_id BIGINT NOT NULL,
    host_id VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- One proctor assigned per exam session/room at a time (nullable = unassigned).
ALTER TABLE exam ADD COLUMN IF NOT EXISTS proctor_id BIGINT REFERENCES proctor(id);
