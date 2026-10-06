CREATE TABLE license_issue_intents (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    actor_public_id UUID NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key UUID NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    license_code_public_id UUID NOT NULL,
    CONSTRAINT uk_license_issue_intents_actor_operation_key
        UNIQUE (actor_public_id, operation, idempotency_key),
    CONSTRAINT fk_license_issue_intents_license_code
        FOREIGN KEY (license_code_public_id) REFERENCES license_codes(public_id)
);
CREATE INDEX idx_license_issue_intents_result ON license_issue_intents(license_code_public_id);
