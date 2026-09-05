package com.pte.examdelivery.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.examdelivery.domain.enums.TimerPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Server-authoritative timing for the attempt's current task (ADR: client timer
 * is UX only). {@code prepDeadline}/{@code responseDeadline} are both computed
 * at task-start (prep collapses immediately into response when prepSeconds=0),
 * so enforcement never depends on the client calling back to "end prep."
 */
@Entity
@Table(name = "timer_states")
@Getter
@Setter
@NoArgsConstructor
public class TimerState extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    private ExamAttempt attempt;

    @Column(nullable = false)
    private int currentOrderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimerPhase phase;

    @Column(nullable = false)
    private Instant taskStartedAt;

    @Column(nullable = false)
    private Instant prepDeadline;

    @Column(nullable = false)
    private Instant responseDeadline;

    /**
     * Section currently governing {@code prepDeadline}/{@code responseDeadline}
     * for a section-scoped section (e.g. READING) — null for task-scoped
     * sections. Lets {@link com.pte.examdelivery.service.TimerService} tell
     * "still inside the same section, reuse its shared deadline" apart from
     * "just entered a new section, compute a fresh one."
     */
    @Column(nullable = true)
    private String activeSection;

    /** Task-local — reset to 0 whenever {@link com.pte.examdelivery.service.TimerService#startTaskTimer} starts a (new or resumed) task. */
    @Column(nullable = false)
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
     * A grace window past {@code responseDeadline} before the window is
     * truly considered expired — the client stops recording/interacting
     * exactly at the deadline (by design, matching the time it was told),
     * but the actual submission (upload + complete + answer POST) is real
     * network round-trip work that unavoidably lands after that instant.
     * With zero grace, a recording-based answer could never successfully
     * submit at all — found via a real end-to-end walkthrough
     * (plans/phat-speaking-api-e2e-verify Phase 3): a genuine, on-time
     * recording was rejected every time despite uploading successfully.
     * Applied uniformly to every task type (not just audio-recording ones)
     * for consistency — every answer path shares the same network-latency
     * reality, just to a smaller degree for typed/selected answers.
     */
    private static final long RESPONSE_WINDOW_GRACE_SECONDS = 15;

    public boolean isResponseWindowExpired(Instant now) {
        return now.isAfter(responseDeadline.plusSeconds(RESPONSE_WINDOW_GRACE_SECONDS));
    }
}
