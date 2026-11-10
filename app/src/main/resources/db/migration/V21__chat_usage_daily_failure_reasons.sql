-- Why the failed messages failed, as counts only: still no student and no text in this table. The reasons are
-- grouped (see ChatFailureBucket) so the admin can tell a provider outage from a bad key or a cut answer.
-- Failures counted before this migration have no known reason, so they are carried into failed_other and the six
-- columns always add up to failed.
ALTER TABLE chat_usage_daily
    ADD COLUMN failed_timeout INTEGER NOT NULL DEFAULT 0 CHECK (failed_timeout >= 0),
    ADD COLUMN failed_rate_limited INTEGER NOT NULL DEFAULT 0 CHECK (failed_rate_limited >= 0),
    ADD COLUMN failed_auth INTEGER NOT NULL DEFAULT 0 CHECK (failed_auth >= 0),
    ADD COLUMN failed_unavailable INTEGER NOT NULL DEFAULT 0 CHECK (failed_unavailable >= 0),
    ADD COLUMN failed_truncated INTEGER NOT NULL DEFAULT 0 CHECK (failed_truncated >= 0),
    ADD COLUMN failed_other INTEGER NOT NULL DEFAULT 0 CHECK (failed_other >= 0);

UPDATE chat_usage_daily SET failed_other = failed;
