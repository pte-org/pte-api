package com.pte.notification.domain;

import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Index;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One recipient of one notification: the delivery state (claimed, retried, delivered) and, once delivered, the inbox
 * entry itself (sequence, delivery time, read time). Atomic delivery SQL stays inside its repository.
 */
@Entity
@Table(name = "notification_recipients", indexes = {
        @Index(name = "idx_notification_recipient_claim", columnList = "status, next_attempt_at"),
        @Index(name = "idx_notification_recipient_lease", columnList = "status, lease_until"),
        @Index(name = "idx_notification_recipient_content_status", columnList = "content_public_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_recipient_content",
                columnNames = {"content_public_id", "recipient_user_public_id"}),
        @UniqueConstraint(name = "uq_notification_recipient_sequence",
                columnNames = {"recipient_user_public_id", "sequence_no"})
})
@Getter
@NoArgsConstructor
public class NotificationRecipient extends BaseEntity {
    @Column(name = "content_public_id", nullable = false)
    private UUID contentPublicId;

    @Column(name = "recipient_user_public_id", nullable = false)
    private UUID recipientUserPublicId;

    @Column(name = "tenant_id", nullable = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InboxDeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claim_token", nullable = true)
    private UUID claimToken;

    @Column(name = "lease_until", nullable = true)
    private Instant leaseUntil;

    @Column(name = "last_failure_code", nullable = true, length = 64)
    private String lastFailureCode;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    /** Position in the recipient's inbox stream; null until the notification is delivered. */
    @Column(name = "sequence_no", nullable = true)
    private Long sequenceNo;

    @Column(name = "delivered_at", nullable = true)
    private Instant deliveredAt;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;
}
