-- Phase 6: iam's projection of admin's tenant governance + idempotency ledger.

CREATE TABLE tenant_registry (
    id               BIGSERIAL PRIMARY KEY,
    public_id        UUID        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    tenant_public_id UUID        NOT NULL UNIQUE,
    status           VARCHAR(20) NOT NULL
);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
