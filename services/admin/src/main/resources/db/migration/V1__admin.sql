-- admin schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `admin` database only.

CREATE TABLE tenants (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    name              VARCHAR(200) NOT NULL UNIQUE,
    organization_type VARCHAR(100) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    package_name      VARCHAR(100) NOT NULL,
    student_limit     INTEGER     NOT NULL
);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
