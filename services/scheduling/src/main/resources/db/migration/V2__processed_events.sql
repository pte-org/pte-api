-- Phase 6: scheduling's idempotency ledger for Kafka consumers.

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
