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
     * as a countdown in the client's app bar. Computed once, right after
     * {@link #begin()}, from the pinned snapshot that exists by then; never
     * recomputed afterward. The client (client-side-exam-timer Phase 3/4) is now
     * the only thing that ever acts on this value — no server-side enforcement
     * reads it (Phase 5 deleted the last of that).
     */
    @Column
    private Instant examEndTime;

    /** Audit fact only — set once at creation from the accepted {@code deviceCheckConfirmed} flag, never re-checked. */
    @Column
    private Instant deviceCheckPassedAt;

    /**
     * Current pinned-item pointer — relocated off the now-deleted {@code TimerState}
     * (client-side-exam-timer refactor, Phase 1) so "which task is current" outlives
     * whatever happens to per-task timing state. Explicit {@code columnDefinition}
     * default so {@code ddl-auto: update}'s {@code ALTER TABLE ... ADD COLUMN ... NOT NULL}
     * succeeds against an already-seeded database (precedent: {@code MediaObject.audioPrompt}).
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int currentOrderIndex;

    /**
     * Task-local audio-replay counters — relocated off the now-deleted
     * {@code TimerState} (Phase 1); reset to 0/null whenever
     * {@link com.pte.examdelivery.service.TimerService#startTask} starts a
     * (new or resumed) task.
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int playCount;

    /**
     * Idempotency for the audio-play endpoint, scoped to the current task only
     * (reset alongside {@link #playCount}). A repeated request with the same
     * key replays {@link #lastPlayAllowed} instead of incrementing again.
     */
    @Column
    private String lastPlayRequestId;

    @Column
    private Boolean lastPlayAllowed;

    /**
     * Section-scoped shared-budget tracking (client-side-exam-timer Phase 2 addendum) —
     * relocated off the now-deleted {@code TimerState} for the same reason as the fields
     * above, but for a different consumer:
     * {@link com.pte.examdelivery.service.TimerService#resolveEffectiveResponseSeconds}
     * uses these to compute how much of a section's shared time budget (currently only
     * READING) remains, so the client can keep receiving a correct, live "remaining seconds"
     * value in {@code TaskView.responseSeconds} purely from a per-task-fetch response —
     * without the client ever needing an absolute deadline timestamp or a repeated poll to
     * stay in sync, and without the client ever seeing more than one task at a time. Null
     * for a task-scoped section (the common case).
     */
    @Column
    private String activeSection;

    @Column
    private Instant sectionStartedAt;

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
