package com.aptis.modules.examdelivery.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.proctor.service.ProctorStatusPushService;

/**
 * Scans for attempts whose heartbeat has gone stale and marks them DISCONNECTED. The DB
 * mutation is a single bulk UPDATE (see
 * {@link ExamAttemptRepository#markTimedOutAttemptsDisconnected}) rather than per-row
 * load+save, per the phase's scale mitigation; a lightweight read-only SELECT beforehand
 * identifies which attempts to notify their proctor about.
 */
@Component
public class ExamAttemptTimeoutScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExamAttemptTimeoutScheduler.class);

    private final ExamAttemptRepository examAttemptRepository;
    private final ProctorStatusPushService proctorStatusPushService;

    public ExamAttemptTimeoutScheduler(
            ExamAttemptRepository examAttemptRepository,
            ProctorStatusPushService proctorStatusPushService) {
        this.examAttemptRepository = examAttemptRepository;
        this.proctorStatusPushService = proctorStatusPushService;
    }

    @Scheduled(fixedRate = ExamDeliveryConstants.HEARTBEAT_SCAN_INTERVAL_MS)
    @Transactional
    public void markTimedOutAttempts() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(ExamDeliveryConstants.HEARTBEAT_TIMEOUT_SECONDS);

        List<ExamAttempt> timingOut = examAttemptRepository.findByStatusInAndLastHeartbeatAtBefore(
                List.of(ExamAttemptStatus.IN_PROGRESS, ExamAttemptStatus.SECTION_SWITCH), cutoff);

        int updated = examAttemptRepository.markTimedOutAttemptsDisconnected(cutoff);
        if (updated > 0) {
            LOGGER.info("Marked {} exam attempt(s) DISCONNECTED after heartbeat timeout", updated);
        }

        for (ExamAttempt attempt : timingOut) {
            proctorStatusPushService.pushStatus(
                    attempt.getExamId(),
                    attempt.getId(),
                    attempt.getStudentId(),
                    ExamAttemptStatus.DISCONNECTED.name(),
                    attempt.getLastHeartbeatAt(),
                    null);
        }
    }
}
