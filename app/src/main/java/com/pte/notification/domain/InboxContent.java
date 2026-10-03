package com.pte.notification.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxTargetType;
import java.util.UUID;

/** Notification-owned persistence mapping; atomic delivery SQL stays inside its repository. */
@Entity @Table(name = "notification_inbox_contents")
@Getter @NoArgsConstructor
public class InboxContent extends BaseEntity {
    @Column(name = "event_key", nullable = false, length = 255, updatable = false)
    private String eventKey;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 48, updatable = false)
    private InboxNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32, updatable = false)
    private InboxCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 16, updatable = false)
    private InboxImportance importance;

    @Column(name = "title", nullable = false, length = 150, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 5000, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32, updatable = false)
    private InboxTargetType targetType;

    @Column(name = "target_public_id", nullable = false, updatable = false)
    private UUID targetPublicId;
}
