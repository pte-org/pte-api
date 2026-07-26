-- Phase 10: exam-delivery's first Kafka consumer (ProctorCommand) needs an idempotency ledger.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
