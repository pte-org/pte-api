package com.pte.practice.internal.service.session;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the start insert so a concurrent idempotency-key race can roll
 * back without poisoning the caller's transaction before it reads the winner.
 */
@Service
public class PracticeSessionPersistenceService {

    private final PracticeSessionRepository sessionRepository;

    public PracticeSessionPersistenceService(PracticeSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSession saveStart(PracticeSession session) {
        return sessionRepository.saveAndFlush(session);
    }

    /**
     * Discards the unfinished attempt and inserts its replacement in one transaction. Both rows fall under the
     * one-active-attempt-per-mock-test unique index, so discarding in the caller's transaction and inserting here
     * would leave the insert waiting on a lock the caller holds until this method returns: a deadlock that
     * Postgres cannot see. The caller's copy of the previous session is left untouched for the same reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeSession replaceStart(Long previousId, Instant discardedAt, PracticeSession session) {
        sessionRepository.findById(previousId)
                .filter(previous -> previous.getStatus() == PracticeSessionStatus.OVERVIEW
                        || previous.getStatus() == PracticeSessionStatus.IN_PROGRESS)
                .ifPresent(previous -> {
                    previous.setStatus(PracticeSessionStatus.DISCARDED);
                    previous.setDiscardedAt(discardedAt);
                    sessionRepository.saveAndFlush(previous);
                });
        return sessionRepository.saveAndFlush(session);
    }
}
