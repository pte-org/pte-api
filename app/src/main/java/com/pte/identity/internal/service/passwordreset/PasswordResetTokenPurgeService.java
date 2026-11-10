package com.pte.identity.internal.service.passwordreset;

import com.pte.identity.internal.config.PasswordResetProperties;
import com.pte.identity.internal.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes reset links that expired more than the retention period ago. A link only matters until it expires, so
 * the table stays small instead of growing with every request.
 */
@Service
public class PasswordResetTokenPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenPurgeService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final Clock clock;
    private final Duration retention;

    public PasswordResetTokenPurgeService(PasswordResetTokenRepository tokenRepository, Clock clock,
            PasswordResetProperties properties) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
        this.retention = Duration.ofDays(properties.getRetentionDays());
    }

    @Scheduled(cron = "${app.password-reset.purge-cron:0 30 3 * * *}")
    @Transactional
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(retention);
        int removed = tokenRepository.deleteExpiredBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} expired password reset links", removed);
        }
        return removed;
    }
}
