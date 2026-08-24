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

    public boolean isResponseWindowExpired(Instant now) {
        return now.isAfter(responseDeadline);
    }
}
