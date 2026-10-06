package com.pte.notification.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** Notification-owned persistence mapping; atomic delivery SQL stays inside its repository. */
@Entity @Table(name = "notification_inbox_streams")
@Getter @NoArgsConstructor
public class InboxRecipientStream extends BaseEntity {
    @Column(name = "recipient_user_public_id", nullable = false)
    private UUID recipientUserPublicId;

    @Column(name = "tenant_id", nullable = true)
    private UUID tenantId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "read_revision", nullable = false)
    private long readRevision;
}
