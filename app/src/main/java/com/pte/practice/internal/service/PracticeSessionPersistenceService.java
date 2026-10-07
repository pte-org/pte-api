package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.repository.PracticeSessionRepository;
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
}
