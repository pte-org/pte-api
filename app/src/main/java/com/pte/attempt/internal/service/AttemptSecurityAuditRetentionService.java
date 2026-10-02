package com.pte.attempt.internal.service;

import com.pte.attempt.internal.repository.AttemptSecurityEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Platform-owned soft retention for student security evidence. */
@Service
public class AttemptSecurityAuditRetentionService {

    private final AttemptSecurityEventRepository securityEventRepository;
    private final int retentionDays;

    public AttemptSecurityAuditRetentionService(
            AttemptSecurityEventRepository securityEventRepository,
            @Value("${attempt.security-audit.retention-days:180}") int retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("attempt security-audit retention-days must be positive");
        }
        this.securityEventRepository = securityEventRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${attempt.security-audit.retention-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpired() {
        markExpired(Instant.now());
    }

    int markExpired(Instant now) {
        return securityEventRepository.markExpiredBefore(now.minus(retentionDays, ChronoUnit.DAYS));
    }
}
