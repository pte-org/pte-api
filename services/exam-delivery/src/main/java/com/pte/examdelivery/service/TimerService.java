package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.TimerPhase;
import com.pte.examdelivery.repository.TimerStateRepository;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative timing (client timer is UX only). Both prep and
 * response deadlines are computed at task-start, so enforcement never depends
 * on a client "prep done" callback — a task's response window starts exactly
 * {@code prepSeconds} after it began, whether or not the student was ready.
 *
 * <p>Sections in {@link #SECTION_SCOPED_SECTIONS} (currently only READING,
 * matching the real PTE exam) share a single deadline across every task in
 * the section instead of getting a fresh per-task deadline — computed once,
 * when the first task of the section starts, as the sum of every task's own
 * {@code prepSeconds + responseSeconds} in that section's contiguous run.
 * Every other task in the section then reuses that same deadline unchanged.
 * All other sections (SPEAKING, WRITING, LISTENING) keep the original
 * per-task behavior.
 */
@Service
public class TimerService {

    private static final Set<String> SECTION_SCOPED_SECTIONS = Set.of("READING");

    private final TimerStateRepository timerStateRepository;

    public TimerService(TimerStateRepository timerStateRepository) {
        this.timerStateRepository = timerStateRepository;
    }

    /**
     * @param allItems every item of the attempt's pinned snapshot, in
     *                  {@code orderIndex} order — only scanned when {@code item}
     *                  is the first task of a new section-scoped section, to sum
     *                  that section's total budget.
     */
    public TimerState startTaskTimer(ExamAttempt attempt, PinnedItemView item, List<PinnedItemView> allItems) {
        TimerState state = timerStateRepository.findByAttemptId(attempt.getId()).orElseGet(TimerState::new);
        Instant now = Instant.now();
        state.setAttempt(attempt);
        state.setCurrentOrderIndex(item.orderIndex());
        state.setPlayCount(0);
        state.setLastPlayRequestId(null);
        state.setLastPlayAllowed(null);

        if (SECTION_SCOPED_SECTIONS.contains(item.section())) {
            if (item.section().equals(state.getActiveSection())) {
                // Still inside the same section-scoped section — reuse the
                // deadline already in place rather than resetting it.
                state.setPhase(TimerPhase.RESPONSE);
            } else {
                state.setActiveSection(item.section());
                state.setPhase(TimerPhase.RESPONSE);
                state.setTaskStartedAt(now);
                state.setPrepDeadline(now);
                state.setResponseDeadline(now.plusSeconds(sectionBudgetSeconds(item, allItems)));
            }
        } else {
            state.setActiveSection(null);
            state.setPhase(item.prepSeconds() > 0 ? TimerPhase.PREP : TimerPhase.RESPONSE);
            state.setTaskStartedAt(now);
            state.setPrepDeadline(now.plusSeconds(item.prepSeconds()));
            state.setResponseDeadline(now.plusSeconds((long) item.prepSeconds() + item.responseSeconds()));
        }

        return timerStateRepository.save(state);
    }

    /**
     * Sums {@code prepSeconds + responseSeconds} across the contiguous run of
     * {@code allItems} sharing {@code item.section()} that contains
     * {@code item.orderIndex()} — items are expected to be grouped by section
     * (matching real PTE section ordering), so a single forward/backward scan
     * from {@code item}'s own position finds the whole run.
     */
    private long sectionBudgetSeconds(PinnedItemView item, List<PinnedItemView> allItems) {
        return allItems.stream()
                .filter(candidate -> item.section().equals(candidate.section()))
                .mapToLong(candidate -> (long) candidate.prepSeconds() + candidate.responseSeconds())
                .sum();
    }

    public boolean isResponseWindowExpired(TimerState state) {
        return state.isResponseWindowExpired(Instant.now());
    }

    public TimerState getState(Long attemptId) {
        return timerStateRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> new IllegalStateException("No timer state for attempt " + attemptId));
    }

    /** Pessimistic write lock — for the audio-play endpoint's check-and-increment (Phase 6 concurrency requirement). */
    public TimerState getStateWithLock(Long attemptId) {
        return timerStateRepository.findWithLockByAttemptId(attemptId)
                .orElseThrow(() -> new IllegalStateException("No timer state for attempt " + attemptId));
    }

    public void save(TimerState state) {
        timerStateRepository.save(state);
    }
}
