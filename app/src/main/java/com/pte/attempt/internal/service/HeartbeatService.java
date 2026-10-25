package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records the "still alive" presence signal for connectivity monitoring as one column on the attempt, written by a
 * targeted update so a ping never bumps the version column and never competes with an in-flight submit.
 */
@Service
public class HeartbeatService {

    private final ExamAttemptRepository attemptRepository;

    public HeartbeatService(ExamAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /** Best-effort presence write in its own transaction, so it can never fail or be rolled back by the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHeartbeat(ExamAttempt attempt) {
        attemptRepository.touchLastSeen(attempt.getId(), Instant.now());
    }
}
