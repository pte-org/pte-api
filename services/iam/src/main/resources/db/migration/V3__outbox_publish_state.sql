-- Relay bookkeeping columns (rabbitmq-outbox-migration Phase 3). Existing
-- rows were already relayed by Debezium's WAL-tail CDC; backfill them to
-- published = true so the new polling relay does not redeliver history.

ALTER TABLE outbox
    ADD COLUMN published        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at     TIMESTAMPTZ,
    ADD COLUMN publish_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN last_error       TEXT,
    ADD COLUMN quarantined      BOOLEAN     NOT NULL DEFAULT FALSE;

UPDATE outbox SET published = TRUE, published_at = occurred_at WHERE published = FALSE;
