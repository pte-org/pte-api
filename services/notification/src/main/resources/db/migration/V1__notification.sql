-- notification schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `notification` database only.

CREATE TABLE user_directory_entries (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID        NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    user_public_id UUID        NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL,
    tenant_id      UUID
);
CREATE INDEX idx_user_directory_tenant ON user_directory_entries (tenant_id);

CREATE TABLE user_directory_roles (
    user_directory_entry_id BIGINT      NOT NULL REFERENCES user_directory_entries (id) ON DELETE CASCADE,
    role                    VARCHAR(50) NOT NULL
);
CREATE INDEX idx_user_directory_roles_entry ON user_directory_roles (user_directory_entry_id);

CREATE TABLE notification_logs (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID        NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    recipient_user_public_id UUID        NOT NULL,
    recipient_email          VARCHAR(255) NOT NULL,
    tenant_id                UUID        NOT NULL,
    notification_type        VARCHAR(30) NOT NULL,
    subject                   VARCHAR(300) NOT NULL,
    body                      TEXT        NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    sent_at                   TIMESTAMPTZ
);
CREATE INDEX idx_notification_logs_tenant ON notification_logs (tenant_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
