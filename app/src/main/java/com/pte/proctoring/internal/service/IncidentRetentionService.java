package com.pte.proctoring.internal.service;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Platform-owned soft retention for student security evidence (AUTO incidents). */
@Service
public class IncidentRetentionService {

    private final ExamIncidentRepository incidentRepository;
    private final int retentionDays;

    public IncidentRetentionService(
            ExamIncidentRepository incidentRepository,
            @Value("${attempt.security-audit.retention-days:180}") int retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException(ProctorConstants.RETENTION_DAYS_INVALID);
        }
        this.incidentRepository = incidentRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${attempt.security-audit.retention-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpired() {
        markExpired(Instant.now());
    }

    int markExpired(Instant now) {
        return incidentRepository.markExpiredEvidenceBefore(now.minus(retentionDays, ChronoUnit.DAYS));
    }
}
