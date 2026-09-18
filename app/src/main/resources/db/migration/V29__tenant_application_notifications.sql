ALTER TABLE notification_logs
    ALTER COLUMN recipient_user_public_id DROP NOT NULL,
    ALTER COLUMN tenant_id DROP NOT NULL;

ALTER TABLE notification_logs
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_logs_dedupe_key
    ON notification_logs (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
