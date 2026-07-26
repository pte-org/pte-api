-- media schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `media` database only.

CREATE TABLE media_objects (
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    tenant_id        UUID        NOT NULL,
    owner_public_id  UUID        NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL UNIQUE,
    status           VARCHAR(20) NOT NULL
);
CREATE INDEX idx_media_objects_owner ON media_objects (owner_public_id);
