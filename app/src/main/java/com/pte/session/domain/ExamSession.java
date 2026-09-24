package com.pte.session.domain;

import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.ExamMode;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.CollectionTable;
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
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
public class ExamSession extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 64)
    private String licenseKey;

    @Column
    private UUID snapshotPublicId;

    @Column
    private UUID templatePublicId;

    @Column
    private Integer templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_mode", length = 32)
    private FormMode formMode = FormMode.SHARED_FORM;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_mode", length = 16)
    private ExamMode examMode = ExamMode.MOCK_TEST;

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

    @Column(name = "generation_job_public_id")
    private UUID generationJobPublicId;

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

    @Embedded
    private ExamPolicy policy = ExamPolicy.mockTestDefault();

    public void open() {
        if (status != SessionStatus.CANCELLED) {
            this.status = SessionStatus.OPEN;
        }
    }

    public void close() {
        if (status != SessionStatus.CANCELLED) {
            this.status = SessionStatus.CLOSED;
        }
    }

    public void cancel() {
        if (status == SessionStatus.DRAFT || status == SessionStatus.PREPARING
                || status == SessionStatus.READY || status == SessionStatus.SCHEDULED) {
            this.status = SessionStatus.CANCELLED;
        }
    }
}
