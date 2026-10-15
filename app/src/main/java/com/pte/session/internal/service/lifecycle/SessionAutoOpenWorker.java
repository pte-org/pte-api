package com.pte.session.internal.service.lifecycle;

import com.pte.session.internal.repository.ExamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Opens scheduled sessions once their persisted start time has arrived. */
@Component
public class SessionAutoOpenWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAutoOpenWorker.class);

    private final ExamSessionRepository sessionRepository;
    private final SessionStateService stateService;
    private final Clock clock;

    public SessionAutoOpenWorker(ExamSessionRepository sessionRepository,
            SessionStateService stateService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.stateService = stateService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${session.auto-open.poll-delay-ms:60000}")
    public void openDueSessionsOnSchedule() {
        openDueSessions();
    }

    public int openDueSessions() {
        Instant now = clock.instant();
        int opened = 0;
        for (var sessionPublicId : sessionRepository.findDueForAutoOpen(now)) {
            try {
                if (stateService.openIfDue(sessionPublicId, now).isPresent()) {
                    opened++;
                }
            } catch (RuntimeException exception) {
                // One invalid entitlement must not prevent other sessions from opening.
                LOGGER.warn("Unable to auto-open exam session {}", sessionPublicId, exception);
            }
        }
        return opened;
    }
}
