-- scheduling schema (Flyway owns DDL; Hibernate ddl-auto=validate). Runs against the `scheduling` database only.

CREATE TABLE snapshot_refs (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    snapshot_public_id UUID       NOT NULL UNIQUE,
    version           INTEGER     NOT NULL,
    name              VARCHAR(300) NOT NULL,
    tenant_id         UUID
);

CREATE TABLE snapshot_ref_items (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID        NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    snapshot_ref_id BIGINT      NOT NULL REFERENCES snapshot_refs (id) ON DELETE CASCADE,
    task_type       VARCHAR(60) NOT NULL,
    section         VARCHAR(20) NOT NULL,
    order_index     INTEGER     NOT NULL
);
CREATE INDEX idx_snapshot_ref_items_ref ON snapshot_ref_items (snapshot_ref_id);

CREATE TABLE exam_sessions (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID        NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    name               VARCHAR(300) NOT NULL,
    tenant_id          UUID        NOT NULL,
    snapshot_public_id UUID        NOT NULL,
    opens_at           TIMESTAMPTZ NOT NULL,
    closes_at          TIMESTAMPTZ NOT NULL,
    status             VARCHAR(20) NOT NULL
);
CREATE INDEX idx_sessions_tenant ON exam_sessions (tenant_id);

CREATE TABLE session_compositions (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID        NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    deleted                   BOOLEAN     NOT NULL DEFAULT FALSE,
    session_id                BIGINT      NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    task_type                 VARCHAR(60) NOT NULL,
    section                   VARCHAR(20) NOT NULL,
    order_index                INTEGER     NOT NULL,
    timing_override_seconds   INTEGER
);
CREATE INDEX idx_compositions_session ON session_compositions (session_id);

CREATE TABLE enrollments (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    session_id        BIGINT      NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    student_public_id UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    CONSTRAINT uq_enrollment UNIQUE (session_id, student_public_id)
);
CREATE INDEX idx_enrollments_session ON enrollments (session_id);

CREATE TABLE proctor_assignments (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    session_id        BIGINT      NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    proctor_public_id UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    CONSTRAINT uq_proctor_assignment UNIQUE (session_id, proctor_public_id)
);
CREATE INDEX idx_proctor_assignments_session ON proctor_assignments (session_id);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    tenant_id      UUID,
    occurred_at    TIMESTAMPTZ  NOT NULL
);
