-- Phase 11: bind every exam session to one purchased exam-package lane.
-- The monolith has no real data yet, so the new NOT NULL columns need no backfill.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE exam_sessions
    ADD COLUMN subscription_id UUID NOT NULL,
    ADD COLUMN license_key VARCHAR(64) NOT NULL,
    ALTER COLUMN capacity SET NOT NULL;

ALTER TABLE enrollments
    ADD COLUMN license_key VARCHAR(64) NOT NULL;

-- Different subscriptions may use the same window; one lane may not.
ALTER TABLE exam_sessions ADD CONSTRAINT no_overlap_per_subscription
    EXCLUDE USING gist (
        subscription_id WITH =,
        tstzrange(opens_at, closes_at) WITH &&
    );

CREATE INDEX idx_sessions_subscription ON exam_sessions (subscription_id);
