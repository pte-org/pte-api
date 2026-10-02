package com.pte.itembank.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only proof that a task type was used by a published template. */
@Entity
@Table(name = "task_type_publication_usage", indexes = {
        @Index(name = "idx_task_type_publication_usage_task", columnList = "task_type_key")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_task_type_publication_usage_template_task",
                columnNames = {"task_type_key", "template_version_public_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class TaskTypePublicationUsage extends BaseEntity {

    @Column(name = "task_type_key", nullable = false, length = 64)
    private String taskTypeKey;

    @Column(name = "template_public_id", nullable = false)
    private UUID templatePublicId;

    @Column(name = "template_version_public_id", nullable = false)
    private UUID templateVersionPublicId;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "actor_public_id")
    private UUID actorPublicId;

    @Column(name = "audit_reference", length = 128)
    private String auditReference;
}
