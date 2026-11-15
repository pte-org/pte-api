package com.pte.practice.internal.service.session;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "Nộp bài": ends a task or skill session early. A session can hold every question of a task type, so the student may
 * stop after some of them; the questions not reached are marked skipped and the session completes, which opens its
 * review. A mock test keeps its own fixed flow, and a session with no answer has nothing to submit. Repeating the call
 * on a completed session is harmless.
 */
@Service
public class PracticeSessionFinishService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseService itemService;
    private final Clock clock;

    public PracticeSessionFinishService(PracticeSessionRepository sessionRepository,
            PracticeResponseService itemService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemService = itemService;
        this.clock = clock;
    }

    /** True when the session is now completed; false when it cannot be submitted (mock test, nothing answered). */
    @Transactional
    public boolean finish(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionPublicId, caller.userId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        if (session.getStatus() == PracticeSessionStatus.COMPLETED) {
            return true;
        }
        boolean open = session.getStatus() == PracticeSessionStatus.IN_PROGRESS;
        boolean answered = itemService.itemsFor(session).stream()
                .anyMatch(item -> item.getStatus() == PracticeResponseStatus.ANSWERED);
        if (!open || session.effectiveKind() == PracticeSessionKind.MOCK_TEST || !answered) {
            return false;
        }
        Instant now = clock.instant();
        for (PracticeResponse item : itemService.itemsFor(session)) {
            if (item.getStatus() == PracticeResponseStatus.PENDING) {
                itemService.skip(item, now);
            }
        }
        session.setStatus(PracticeSessionStatus.COMPLETED);
        session.setCompletedAt(now);
        session.setLastActivityAt(now);
        sessionRepository.saveAndFlush(session);
        return true;
    }
}
