package com.pte.notification.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.pte.notification.domain.enums.InboxDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/** Notification-owned persistence mapping; atomic delivery SQL stays inside its repository. */
@Entity @Table(name = "notification_inbox_deliveries")
@Getter @NoArgsConstructor
public class InboxDeliveryIntent extends BaseEntity {
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
}
