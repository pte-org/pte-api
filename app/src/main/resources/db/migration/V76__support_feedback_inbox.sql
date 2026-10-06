-- Widen the existing immutable inbox contract for support-ticket feedback events.
-- Rollback: disable notification.inbox.enabled; retain durable support notification history.
ALTER TABLE notification_inbox_contents
    DROP CONSTRAINT IF EXISTS notification_inbox_contents_notification_type_check;
ALTER TABLE notification_inbox_contents
    ADD CONSTRAINT notification_inbox_contents_notification_type_check
    CHECK (notification_type IN (
        'APPLICATION_SUBMITTED', 'PLATFORM_ANNOUNCEMENT', 'SESSION_CLOSING_SOON',
        'SESSION_GRADING_COMPLETED', 'COMMERCIAL_OUTCOME_CONFIRMED', 'ORDER_EXPIRED',
        'SUBSCRIPTION_REVOKED', 'SUPPORT_TICKET_SUBMITTED', 'SUPPORT_TICKET_NOTE_ADDED',
        'SUPPORT_TICKET_STATUS_CHANGED'));

ALTER TABLE notification_inbox_contents
    DROP CONSTRAINT IF EXISTS notification_inbox_contents_category_check;
ALTER TABLE notification_inbox_contents
    ADD CONSTRAINT notification_inbox_contents_category_check
    CHECK (category IN ('SYSTEM_NOTICE', 'MAINTENANCE', 'SESSION', 'APPLICATION', 'BILLING', 'SUPPORT'));

ALTER TABLE notification_inbox_contents
    DROP CONSTRAINT IF EXISTS notification_inbox_contents_target_type_check;
ALTER TABLE notification_inbox_contents
    ADD CONSTRAINT notification_inbox_contents_target_type_check
    CHECK (target_type IN (
        'APPLICATION', 'ANNOUNCEMENT', 'SESSION', 'ORDER', 'SUBSCRIPTION', 'QUOTA', 'SUPPORT_TICKET'));
