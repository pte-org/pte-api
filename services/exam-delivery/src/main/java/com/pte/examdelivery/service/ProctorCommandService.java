package com.pte.examdelivery.service;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.event.AttemptSubmittedEvent;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies a proctor's {@code ProctorCommand} (phase-10). Deliberately separate
 * from {@link AttemptService} (already at the ≤5-public-method ceiling) AND a
 * distinct authorization path: the actor here is a verified proctor command
 * already tenant-scoped by the producer, not the student themselves — no
 * ownership check, only a tenant check. A silent no-op if the attempt doesn't
 * exist or isn't {@code IN_PROGRESS} (honest completion, same convention as
 * scoring's consumers — a stale/duplicate/late command is not an error).
 *
 * <p>{@code extendResponseTime} (EXTEND_TIME) removed (client-side-exam-timer
 * Phase 5) along with {@code TimerState} — accepted capability loss, see
 * {@code ProctorCommandEvent}'s doc comment.
 */
@Service
public class ProctorCommandService {

    private final ExamAttemptRepository attemptRepository;
    private final OutboxWriter outboxWriter;

    public ProctorCommandService(ExamAttemptRepository attemptRepository, OutboxWriter outboxWriter) {
        this.attemptRepository = attemptRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void forceSubmit(UUID attemptPublicId, UUID tenantId) {
        inProgressAttempt(attemptPublicId, tenantId).ifPresent(attempt -> {
            attempt.submit();
            attemptRepository.save(attempt);
            outboxWriter.write(ExamDeliveryConstants.AGGREGATE_ATTEMPT, attempt.getPublicId().toString(),
                    ExamDeliveryConstants.EVENT_ATTEMPT_SUBMITTED,
                    new AttemptSubmittedEvent(attempt.getPublicId(), attempt.getSessionPublicId(),
                            attempt.getStudentPublicId(), attempt.getTenantId()),
                    attempt.getTenantId());
        });
    }

    private Optional<ExamAttempt> inProgressAttempt(UUID attemptPublicId, UUID tenantId) {
        return attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)
                .filter(attempt -> attempt.getStatus() == AttemptStatus.IN_PROGRESS);
    }
}
