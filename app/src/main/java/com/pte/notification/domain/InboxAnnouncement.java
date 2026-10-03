package com.pte.notification.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Version;

/** Notification-owned persistence mapping; atomic delivery SQL stays inside its repository. */
@Entity @Table(name = "notification_inbox_announcements")
@Getter @NoArgsConstructor
public class InboxAnnouncement extends BaseEntity {
    @Column(name = "author_user_public_id", nullable = false)
    private UUID authorUserPublicId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, length = 5000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private InboxCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 16)
    private InboxImportance importance;

    @Column(name = "affected_from", nullable = true)
    private Instant affectedFrom;

    @Column(name = "affected_until", nullable = true)
    private Instant affectedUntil;

    @Column(name = "published_content_public_id", nullable = true)
    private UUID publishedContentPublicId;

    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    @Column(name = "correction_of_public_id", nullable = true)
    private UUID correctionOfPublicId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
