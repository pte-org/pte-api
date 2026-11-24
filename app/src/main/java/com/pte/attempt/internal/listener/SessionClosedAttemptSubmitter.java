package com.pte.attempt.internal.listener;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.session.dto.event.SessionClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Submits every attempt still in progress when its session closes: once the session is closed the student can no
 * longer reach the server, so the attempt would otherwise stay "in progress" forever and be left out of grading.
 * What counts is exactly what already reached the server; answers that were never synced are simply not there.
 *
 * <p>Runs synchronously inside the closing transaction (a plain {@link EventListener}, not an after-commit one),
 * so the session is never closed with attempts still open. The session row is locked by the close, which
 * serializes this against every student and proctor operation on its attempts.
 */
@Component
public class SessionClosedAttemptSubmitter {

    private static final Logger log = LoggerFactory.getLogger(SessionClosedAttemptSubmitter.class);

    private final ExamAttemptRepository attemptRepository;
    private final Clock clock;

    public SessionClosedAttemptSubmitter(ExamAttemptRepository attemptRepository, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.clock = clock;
    }

    @EventListener
    public void onSessionClosed(SessionClosedEvent event) {
        List<ExamAttempt> open = attemptRepository
                .findBySessionPublicIdAndTenantIdAndStatus(event.sessionPublicId(), event.tenantId(),
                        AttemptStatus.IN_PROGRESS)
                .stream().filter(attempt -> !attempt.isDeleted()).toList();
        if (open.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        open.forEach(attempt -> attempt.submit(now));
        attemptRepository.saveAll(open);
        log.info("Submitted {} attempt(s) still in progress when session {} closed", open.size(),
                event.sessionPublicId());
    }
}
