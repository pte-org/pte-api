package com.pte.session.domain;

import com.pte.session.domain.enums.SessionGradingStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.converter.ExamModeCompatibilityConverter;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A scheduled exam window, referencing a published assessment snapshot by
 * {@code publicId} (never a cross-module JOIN — module boundary is code, not
 * FK). A student pins every item of that snapshot at attempt-create time —
 * there is no host-chosen subset (the random-exam-generation step already
 * applies the skill selection at exam-creation time, not delivery time).
 * Subscription and license references are scalar denormalized values;
 * billing remains a separate module, consulted only as a creation-time gate
 * (window/capacity/overlap).
 */
@Entity
@Table(name = "exam_sessions", indexes = {
        @Index(name = "idx_sessions_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sessions_subscription", columnList = "subscription_id")
})
@Getter
@Setter
public class ExamSession extends BaseEntity {

    public ExamSession() {
        super();
    }

    @Column(nullable = false)
    private String name;

    /** Human-readable exam code ({TENANT}-{YYMMDD}-{RAND4}); set once at creation, never changed. */
    @Column(name = "session_code", nullable = false, unique = true, updatable = false, length = 24)
    private String sessionCode;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 64)
    private String licenseKey;

    /** The single frozen exam every student of this session sits; built when the session is generated. */
    @Column
    private UUID examVersionPublicId;

    @Column
    private UUID examDefinitionPublicId;

    @Column
    private Integer examDefinitionVersion;

    @Convert(converter = ExamModeCompatibilityConverter.class)
    @Column(name = "exam_mode", length = 16)
    private ExamMode examMode = ExamMode.OFFICIAL_EXAM;

    @Enumerated(EnumType.STRING)
    @Column(name = "reuse_policy", length = 40)
    private ReusePolicy reusePolicy = ReusePolicy.ALLOW;

    @Column(name = "series_key", length = 128)
    private String seriesKey;

    /** Skill sections selected for this exam; empty legacy rows are resolved from their pinned template. */
    @ElementCollection
    @CollectionTable(name = "exam_session_selected_skills", joinColumns = @JoinColumn(name = "session_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_exam_session_selected_skills_session_skill",
                    columnNames = {"session_id", "skill"}))
    @Column(name = "skill", nullable = false, length = 16)
    private Set<String> selectedSkills = new LinkedHashSet<>();

    /** Additional submissions allowed after the initial attempt. */
    @Column(name = "max_retries_per_student", nullable = false)
    private int maxRetriesPerStudent = 0;

    @Version
    @Column(name = "draft_version", nullable = false)
    private Long draftVersion = 0L;

    @Column(nullable = false)
    private Instant opensAt;

    @Column(nullable = false)
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    /** Snapshot of the requested per-session capacity, bounded by the subscription cap. */
    @Column(nullable = false)
    private Integer capacity;

    /**
     * Grading and publication state of the session, written by the scoring and reporting phases: the id of the
     * report publication that froze the grading cohort and when it was locked. Null until results are published.
     */
    @Column(name = "publication_public_id")
    private UUID publicationPublicId;

    @Column(name = "publication_locked_at")
    private Instant publicationLockedAt;

    /** Grading of the closed session: who grades it (marking mode), when the host froze it and when it completed. */
    @Column(name = "grading_marking_mode", length = 24)
    private String gradingMarkingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", length = 16)
    private SessionGradingStatus gradingStatus;

    @Column(name = "grading_preview_version", length = 128)
    private String gradingPreviewVersion;

    @Column(name = "grading_frozen_by_public_id")
    private UUID gradingFrozenByPublicId;

    @Column(name = "grading_frozen_at")
    private Instant gradingFrozenAt;

    @Column(name = "grading_completed_at")
    private Instant gradingCompletedAt;

    @Embedded
    private ExamPolicy policy = ExamPolicy.mockTestDefault();

    public void open() {
        requireStatus(SessionStatus.SCHEDULED);
        this.status = SessionStatus.OPEN;
    }

    public void close() {
        requireStatus(SessionStatus.OPEN);
        this.status = SessionStatus.CLOSED;
    }

    public void cancel() {
        if (status != SessionStatus.READY && status != SessionStatus.SCHEDULED) {
            throw new IllegalStateException("Only ready or scheduled sessions can be cancelled");
        }
        this.status = SessionStatus.CANCELLED;
    }

    private void requireStatus(SessionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Session must be " + expected + " before this transition");
        }
    }

    /** Fixes how the closed session is graded; a session is frozen once. */
    public void freezeGrading(String markingMode, String previewVersion, UUID frozenByPublicId, Instant frozenAt) {
        if (gradingStatus != null) {
            throw new IllegalStateException(SessionConstants.GRADING_ALREADY_FROZEN);
        }
        this.gradingMarkingMode = markingMode;
        this.gradingPreviewVersion = previewVersion;
        this.gradingFrozenByPublicId = frozenByPublicId;
        this.gradingFrozenAt = frozenAt;
        this.gradingStatus = SessionGradingStatus.FROZEN;
    }

    /** @return true only on the transition that completes grading, so completion is announced once */
    public boolean completeGrading(Instant completedAt) {
        if (gradingStatus != SessionGradingStatus.FROZEN) {
            return false;
        }
        this.gradingStatus = SessionGradingStatus.COMPLETED;
        this.gradingCompletedAt = completedAt;
        return true;
    }

    /** Locks the published results; repeating the same publication is a no-op, another one is rejected. */
    public void lockPublication(UUID publicationPublicId, Instant lockedAt) {
        if (this.publicationPublicId != null) {
            if (!this.publicationPublicId.equals(publicationPublicId)) {
                throw new IllegalStateException(SessionConstants.PUBLICATION_ALREADY_LOCKED);
            }
            return;
        }
        this.publicationPublicId = publicationPublicId;
        this.publicationLockedAt = lockedAt;
    }
}
