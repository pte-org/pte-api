-- Ledger for one-time, cross-service projection backfills.
--
-- A Flyway migration can create an empty projection table, but it cannot fill
-- it: the source rows live in another service's database and are only reachable
-- over HTTP. RabbitMQ will not close that gap either — `outbox.iam.exchange` is
-- a topic exchange, so every event published before this service first declared
-- its queue was discarded with no consumer to receive it. Rows that predate the
-- projection are therefore invisible until something explicitly rebuilds them.
--
-- This table is that "something"'s bookkeeping: each named backfill is claimed
-- exactly once (INSERT ... ON CONFLICT DO NOTHING), and `completed_at` stays
-- NULL until the rebuild actually succeeds. A claim whose run fails is deleted
-- so the next boot retries it.
CREATE TABLE IF NOT EXISTS projection_backfills (
    name VARCHAR(128) PRIMARY KEY,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    rows_applied INTEGER
);
