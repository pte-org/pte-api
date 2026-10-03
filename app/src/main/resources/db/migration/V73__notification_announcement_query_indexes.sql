-- Announcement management and aggregate delivery/read summaries.
-- Additive only; published content and audience records remain immutable.
CREATE INDEX idx_inbox_announcement_management
    ON notification_inbox_announcements (created_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX idx_inbox_delivery_content_status
    ON notification_inbox_deliveries (content_public_id, status)
    WHERE deleted = FALSE;

CREATE INDEX idx_inbox_item_content_read
    ON notification_inbox_items (content_public_id, read_at)
    WHERE deleted = FALSE;
