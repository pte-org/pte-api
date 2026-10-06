package com.pte.notification.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** Notification-owned persistence mapping; atomic delivery SQL stays inside its repository. */
@Entity @Table(name = "notification_inbox_items")
@Getter @NoArgsConstructor
public class InboxItem extends BaseEntity {
    @Column(name = "delivery_public_id", nullable = false)
    private UUID deliveryPublicId;

    @Column(name = "content_public_id", nullable = false)
    private UUID contentPublicId;

    @Column(name = "recipient_user_public_id", nullable = false)
    private UUID recipientUserPublicId;

    @Column(name = "tenant_id", nullable = true)
    private UUID tenantId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;
}
