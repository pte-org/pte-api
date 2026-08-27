package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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

/**
 * The critical-path aggregate. Once IN_PROGRESS, this attempt (plus its
 * {@link PinnedExamSnapshot}) is the only data exam-delivery needs — no
 * outbound call occurs for the rest of the attempt. The DB unique constraint on
 * (session, student) is the actual double-attempt guard, not an app-level check
 * (CODING_STANDARDS_API.md concurrency rule); {@code @Version} guards concurrent
 * writes to the same attempt (e.g. a racing timer-expiry auto-submit and a
 * manual submit).
 */
@Entity
@Table(name = "exam_attempts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_public_id", "student_public_id"})
}, indexes = {
        @Index(name = "idx_attempts_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt extends BaseEntity {

    @Column(name = "session_public_id", nullable = false)
    private UUID sessionPublicId;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.CREATED;

    @Column
    private Instant startedAt;

    @Column
    private Instant submittedAt;

    /**
     * {@code startedAt} plus the sum of every pinned item's own
     * {@code prepSeconds + responseSeconds} — the whole-attempt deadline shown
     * as a countdown in the client's app bar (distinct from
     * {@link TimerState}'s per-task/section deadlines). Computed once, right
     * after {@link #begin()}, from the pinned snapshot that exists by then;
     * never recomputed afterward even if a later task is auto-expired.
     */
    @Column
    private Instant examEndTime;

    /** Audit fact only — set once at creation from the accepted {@code deviceCheckConfirmed} flag, never re-checked. */
    @Column
    private Instant deviceCheckPassedAt;

    @OneToOne(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PinnedExamSnapshot pinnedSnapshot;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AttemptAnswer> answers = new ArrayList<>();

    @Version
    private Long version;

    public void begin() {
        this.status = AttemptStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void submit() {
        this.status = AttemptStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public boolean isInProgress() {
        return status == AttemptStatus.IN_PROGRESS;
    }
}
