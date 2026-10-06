CREATE TABLE support_ticket (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID        NOT NULL UNIQUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted                  BOOLEAN     NOT NULL DEFAULT false,
    version                  BIGINT      NOT NULL DEFAULT 0,
    tenant_id                UUID        NOT NULL,
    submitter_user_public_id UUID        NOT NULL,
    category                 VARCHAR(32) NOT NULL,
    description              VARCHAR(2000) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    entity_type              VARCHAR(32),
    entity_id                VARCHAR(36),

    CONSTRAINT chk_support_ticket_entity_pair
        CHECK ((entity_type IS NULL) = (entity_id IS NULL)),

    CONSTRAINT chk_support_ticket_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),

    CONSTRAINT chk_support_ticket_entity_id_uuid
        CHECK (entity_id IS NULL OR entity_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
);

CREATE INDEX idx_support_ticket_tenant_status_created
    ON support_ticket (tenant_id, status, created_at);

CREATE TABLE support_ticket_note (
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          BOOLEAN     NOT NULL DEFAULT false,
    ticket_id        BIGINT      NOT NULL REFERENCES support_ticket (id),
    ticket_public_id UUID        NOT NULL,
    admin_public_id  UUID        NOT NULL,
    content          TEXT        NOT NULL
);

CREATE INDEX idx_support_ticket_note_ticket_created
    ON support_ticket_note (ticket_id, created_at);

CREATE INDEX idx_support_ticket_note_ticket_public_id
    ON support_ticket_note (ticket_public_id);
