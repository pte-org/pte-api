ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS extra_time_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS flagged BOOLEAN NOT NULL DEFAULT FALSE;

-- Immutable audit trail for the 4 privileged proctor actions. Application layer only ever
-- inserts (no UPDATE/DELETE code path exists) — revoking DB-level UPDATE/DELETE grants for
-- the app role is an infra/DBA follow-up, not something a migration script can enforce.
CREATE TABLE IF NOT EXISTS proctor_action (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    actor_proctor_id BIGINT NOT NULL REFERENCES proctor(id),
    target_attempt_id BIGINT REFERENCES exam_attempt(id),
    action_type VARCHAR(30) NOT NULL
        CHECK (action_type IN ('FORCE_SUBMIT', 'EXTEND_TIME', 'FLAG_VIOLATION', 'BROADCAST_ANNOUNCEMENT')),
    action_timestamp TIMESTAMPTZ NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_proctor_action_target_attempt ON proctor_action(target_attempt_id);
CREATE INDEX IF NOT EXISTS idx_proctor_action_actor ON proctor_action(actor_proctor_id);
