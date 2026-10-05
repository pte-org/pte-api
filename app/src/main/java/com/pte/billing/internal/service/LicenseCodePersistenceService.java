package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates license-code inserts so a unique-code collision can be retried. */
@Service
public class LicenseCodePersistenceService {

    private final LicenseCodeRepository licenseCodeRepository;
    private final PlanRepository planRepository;

    public LicenseCodePersistenceService(LicenseCodeRepository licenseCodeRepository, PlanRepository planRepository) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LicenseCode save(LicenseCode licenseCode) {
        // Eligibility and insert share this transaction; archive/edit use the same Plan lock.
        Plan plan = planRepository.findByPublicIdForUpdate(licenseCode.getPlanId())
                .filter(p -> !p.isDeleted()).orElseThrow(PlanNotFoundException::new);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
        }
        return licenseCodeRepository.saveAndFlush(licenseCode);
    }
}
