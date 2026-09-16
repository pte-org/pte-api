package com.pte.billing.internal.service;

import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Marks issued codes expired; redeem still checks the timestamp defensively. */
@Service
public class LicenseCodeExpirationService {

    private static final Logger log = LoggerFactory.getLogger(LicenseCodeExpirationService.class);

    private final LicenseCodeRepository licenseCodeRepository;

    public LicenseCodeExpirationService(LicenseCodeRepository licenseCodeRepository) {
        this.licenseCodeRepository = licenseCodeRepository;
    }

    @Scheduled(cron = "${billing.license-code-expiration.cron:0 15 * * * *}")
    @Transactional
    public int expireDueCodes() {
        int expired = licenseCodeRepository.markExpired(Instant.now(), LicenseCodeStatus.ISSUED,
                LicenseCodeStatus.EXPIRED);
        if (expired > 0) {
            log.info("Expired {} billing license codes", expired);
        }
        return expired;
    }
}
