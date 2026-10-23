package com.pte.rubric.domain;

import com.pte.rubric.domain.enums.RubricChannel;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned academic rubric. Published and retired rows are immutable by service policy. */
@Entity
@Table(name = "rubric_revisions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rubric_revision_key_version", columnNames = {"rubric_key", "version"})
}, indexes = {
        @Index(name = "idx_rubric_revision_task_status", columnList = "task_type_key,channel,status"),
        @Index(name = "idx_rubric_revision_author", columnList = "author_user_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RubricRevision extends BaseEntity {

    @Column(name = "rubric_key", nullable = false, length = 128)
    private String rubricKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "task_type_key", nullable = false, length = 64)
    private String taskTypeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RubricChannel channel = RubricChannel.PRACTICE;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RubricStatus status = RubricStatus.DRAFT;

    @Column(name = "author_user_public_id")
    private UUID authorUserPublicId;

    @Column(name = "reviewer_user_public_id")
    private UUID reviewerUserPublicId;

    @Column(name = "publisher_user_public_id")
    private UUID publisherUserPublicId;

    @Column(name = "change_reason", length = 1000)
    private String changeReason;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "document_json", nullable = false, columnDefinition = "text")
    private String documentJson;

    @Column(name = "references_json", columnDefinition = "text")
    private String referencesJson;

    @Column(name = "anchors_json", columnDefinition = "text")
    private String anchorsJson;

    @Column(name = "canonical_hash", length = 64)
    private String canonicalHash;

    @Column(name = "runtime_compatibility_hash", length = 64)
    private String runtimeCompatibilityHash;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Version
    private Long entityVersion;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<RubricCriterion> criteria = new ArrayList<>();

    public void addCriterion(RubricCriterion criterion) {
        criterion.setRevision(this);
        criteria.add(criterion);
    }
}
