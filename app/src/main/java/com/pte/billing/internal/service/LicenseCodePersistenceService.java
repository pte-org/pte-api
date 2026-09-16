package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates license-code inserts so a unique-code collision can be retried. */
@Service
public class LicenseCodePersistenceService {

    private final LicenseCodeRepository licenseCodeRepository;

    public LicenseCodePersistenceService(LicenseCodeRepository licenseCodeRepository) {
        this.licenseCodeRepository = licenseCodeRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LicenseCode save(LicenseCode licenseCode) {
        return licenseCodeRepository.saveAndFlush(licenseCode);
    }
}
