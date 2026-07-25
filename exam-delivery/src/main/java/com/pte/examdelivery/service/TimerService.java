package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.TimerPhase;
import com.pte.examdelivery.repository.TimerStateRepository;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Server-authoritative timing (client timer is UX only). Both prep and
 * response deadlines are computed at task-start, so enforcement never depends
 * on a client "prep done" callback — a task's response window starts exactly
 * {@code prepSeconds} after it began, whether or not the student was ready.
 */
@Service
public class TimerService {

    private final TimerStateRepository timerStateRepository;

    public TimerService(TimerStateRepository timerStateRepository) {
        this.timerStateRepository = timerStateRepository;
    }

    public TimerState startTaskTimer(ExamAttempt attempt, PinnedItemView item) {
        TimerState state = timerStateRepository.findByAttemptId(attempt.getId()).orElseGet(TimerState::new);
        Instant now = Instant.now();
        state.setAttempt(attempt);
        state.setCurrentOrderIndex(item.orderIndex());
        state.setPhase(item.prepSeconds() > 0 ? TimerPhase.PREP : TimerPhase.RESPONSE);
        state.setTaskStartedAt(now);
        state.setPrepDeadline(now.plusSeconds(item.prepSeconds()));
        state.setResponseDeadline(now.plusSeconds((long) item.prepSeconds() + item.responseSeconds()));
        return timerStateRepository.save(state);
    }

    public boolean isResponseWindowExpired(TimerState state) {
        return state.isResponseWindowExpired(Instant.now());
    }

    public TimerState getState(Long attemptId) {
        return timerStateRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> new IllegalStateException("No timer state for attempt " + attemptId));
    }
}
