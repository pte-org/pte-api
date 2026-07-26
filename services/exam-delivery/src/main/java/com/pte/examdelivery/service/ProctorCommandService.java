package com.pte.examdelivery.service;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.event.AttemptSubmittedEvent;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import com.pte.examdelivery.repository.TimerStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies a proctor's {@code ProctorCommand} (phase-10). Deliberately separate
 * from {@link AttemptService} (already at the ≤5-public-method ceiling) AND a
 * distinct authorization path: the actor here is a verified proctor command
 * already tenant-scoped by the producer, not the student themselves — no
 * ownership check, only a tenant check. Both methods are silent no-ops if the
 * attempt doesn't exist or isn't {@code IN_PROGRESS} (honest completion, same
 * convention as scoring's consumers — a stale/duplicate/late command is not
 * an error).
 */
@Service
public class ProctorCommandService {

    private final ExamAttemptRepository attemptRepository;
    private final TimerStateRepository timerStateRepository;
    private final OutboxWriter outboxWriter;

    public ProctorCommandService(ExamAttemptRepository attemptRepository, TimerStateRepository timerStateRepository,
                                 OutboxWriter outboxWriter) {
        this.attemptRepository = attemptRepository;
        this.timerStateRepository = timerStateRepository;
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

    @Transactional
    public void extendResponseTime(UUID attemptPublicId, UUID tenantId, int extraSeconds) {
        inProgressAttempt(attemptPublicId, tenantId).ifPresent(attempt -> {
            TimerState timer = timerStateRepository.findByAttemptId(attempt.getId()).orElse(null);
            if (timer == null) {
                return;
            }
            timer.setResponseDeadline(timer.getResponseDeadline().plusSeconds(extraSeconds));
            timerStateRepository.save(timer);
        });
    }

    private Optional<ExamAttempt> inProgressAttempt(UUID attemptPublicId, UUID tenantId) {
        return attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)
                .filter(attempt -> attempt.getStatus() == AttemptStatus.IN_PROGRESS);
    }
}
