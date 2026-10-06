-- Phase 03: stale-form protection for the platform Plan catalog.
-- Expand-first: existing catalog rows retain all values and start at version 0.
ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
