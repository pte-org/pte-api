package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.AttemptHeartbeat;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.repository.AttemptHeartbeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records the "still alive" presence signal for the parallel connectivity-monitoring
 * feature (client-side-exam-timer Phase 2, FR-04) — entirely separate storage from the
 * former {@code TimerState}/deadline enforcement (deleted in Phase 5), by design.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final AttemptHeartbeatRepository heartbeatRepository;

    public HeartbeatService(AttemptHeartbeatRepository heartbeatRepository) {
        this.heartbeatRepository = heartbeatRepository;
    }

    /**
     * Upsert-by-attempt: find-or-create, stamp {@link AttemptHeartbeat#getLastSeenAt()}
     * (see that field's doc comment for why {@code updatedAt} alone doesn't work), then save.
     *
     * <p>{@code REQUIRES_NEW} (code-reviewer HIGH finding, fixed): two racing first-ever
     * calls for the same attempt could both miss the {@code findByAttemptId} and both
     * attempt an insert — Postgres poisons the WHOLE transaction once the loser's insert
     * hits the unique constraint on {@code attempt_id}, so catching the exception alone
     * (in the same transaction as the caller) does NOT stop it from resurfacing as a
     * {@code TransactionSystemException} at the caller's own commit. Running this in its
     * own physical transaction contains that abort to just this call — a best-effort,
     * fire-and-forget presence write that should never be able to fail the caller's own
     * unrelated work (or vice versa: this write always durably lands independent of
     * whatever the caller does afterward).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHeartbeat(ExamAttempt attempt) {
        AttemptHeartbeat heartbeat = heartbeatRepository.findByAttemptId(attempt.getId())
                .orElseGet(AttemptHeartbeat::new);
        heartbeat.setAttempt(attempt);
        heartbeat.setLastSeenAt(Instant.now());
        try {
            heartbeatRepository.save(heartbeat);
        } catch (DataIntegrityViolationException ex) {
            // Lost a first-insert race to a concurrent heartbeat call for the same attempt.
            // Logged (not silently discarded) since this exception type also covers
            // genuinely different constraint violations, not just this expected race.
            log.warn("Heartbeat insert race for attempt {} — row now exists via the other writer", attempt.getId(), ex);
        }
    }
}
