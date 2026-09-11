package com.pte.notification.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One outbound notification attempt. Terminal states are {@code SENT}/{@code FAILED} — {@code EmailWorker} owns the transition. */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_logs_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog extends BaseEntity {

    @Column(name = "recipient_user_public_id", nullable = false)
    private UUID recipientUserPublicId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
