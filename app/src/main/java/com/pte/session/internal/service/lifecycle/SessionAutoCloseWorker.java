package com.pte.session.internal.service.lifecycle;

import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Closes OPEN sessions after their persisted close time without reopening them. */
@Component
public class SessionAutoCloseWorker {

    private final ExamSessionRepository sessionRepository;
    private final SessionStateService stateService;
    private final Clock clock;

    public SessionAutoCloseWorker(ExamSessionRepository sessionRepository,
            SessionStateService stateService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${session.auto-close.poll-delay-ms:60000}")
    public void closeDueSessionsOnSchedule() {
        closeDueSessions();
    }

    public int closeDueSessions() {
        Instant now = clock.instant();
        int closed = 0;
        for (var sessionPublicId : sessionRepository.findDueForAutoClose(now)) {
            if (stateService.closeIfDue(sessionPublicId, now).isPresent()) {
                closed++;
            }
        }
        return closed;
    }
}
