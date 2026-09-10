package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Presence signal for the parallel connectivity-monitoring feature — client-side-exam-timer
 * Phase 2 (FR-04). Deliberately its own table, decoupled from the former {@code TimerState}
 * (deleted in Phase 5) and from {@link ExamAttempt}'s own {@code @Version}-guarded row (heavily written by
 * {@code playAudio}/{@code processAnswer}/{@code advanceUntilLiveOrComplete} — see
 * {@code AttemptService}'s locking doc comments; adding a ~15s-cadence write onto that same row
 * would only add more contention to the exact lock already fixed in Phase 1).
 *
 * <p><b>{@link #lastSeenAt} is explicit, not {@link BaseEntity#getUpdatedAt()}</b> (code-reviewer
 * HIGH finding, fixed): Hibernate's dirty-checking for a {@code @OneToOne}/{@code @ManyToOne}
 * association compares the associated entity's identifier, not object identity — re-setting
 * {@code attempt} to the same already-associated row on every repeat heartbeat call is NOT
 * considered a change, so with no other mutable field the entity is clean at flush time, no
 * UPDATE is issued, and {@code @UpdateTimestamp} (which only fires as part of an actual UPDATE)
 * never runs — {@code updatedAt} would silently freeze at creation time forever. An explicit
 * field set to {@code Instant.now()} on every call guarantees a real dirty write every time.
 */
@Entity
@Table(name = "attempt_heartbeats")
@Getter
@Setter
@NoArgsConstructor
public class AttemptHeartbeat extends BaseEntity {

    /**
     * {@code unique = true} explicit (code-reviewer LOW finding, fixed) rather than relying
     * on Hibernate's implicit default for a unidirectional {@code @OneToOne} — a future
     * refactor to {@code @ManyToOne} would silently drop that implicit guarantee along with
     * it, undermining {@link com.pte.examdelivery.service.HeartbeatService}'s duplicate-row
     * race handling.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private ExamAttempt attempt;

    @Column(nullable = false)
    private Instant lastSeenAt;
}
