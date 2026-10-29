-- Extend the durable inbox allowlist without rewriting the baseline migration.
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_notification_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_notification_type_check
        CHECK (notification_type IN (
            'APPLICATION_SUBMITTED',
            'PLATFORM_ANNOUNCEMENT',
            'SESSION_CLOSING_SOON',
            'SESSION_GRADING_COMPLETED',
            'SESSION_PREVIEW_READY',
            'COMMERCIAL_OUTCOME_CONFIRMED',
            'ORDER_EXPIRED',
            'SUBSCRIPTION_REVOKED',
            'SUPPORT_TICKET_SUBMITTED',
            'SUPPORT_TICKET_NOTE_ADDED',
            'SUPPORT_TICKET_STATUS_CHANGED'
        ));
