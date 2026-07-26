-- iam schema (Flyway owns DDL; Hibernate ddl-auto=validate).
-- Database-per-service: this runs against the `iam` database only.

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    email       VARCHAR(320) NOT NULL UNIQUE,
    full_name   VARCHAR(200) NOT NULL,
    tenant_id   UUID,
    status      VARCHAR(20) NOT NULL
);

CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE login_hashes (
    id         BIGSERIAL PRIMARY KEY,
    public_id  UUID        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    hash       VARCHAR(255) NOT NULL
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    public_id  UUID        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
