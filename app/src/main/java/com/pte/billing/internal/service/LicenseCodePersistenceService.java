package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.LicenseIssueIntent;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.repository.LicenseIssueIntentRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** Isolates license-code inserts so a unique-code collision can be retried. */
@Service
public class LicenseCodePersistenceService {

    private final LicenseCodeRepository licenseCodeRepository;
    private final PlanRepository planRepository;
    private final LicenseIssueIntentRepository intentRepository;
    private final Clock clock;

    public LicenseCodePersistenceService(LicenseCodeRepository licenseCodeRepository, PlanRepository planRepository,
            LicenseIssueIntentRepository intentRepository, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.intentRepository = intentRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<LicenseIssueReceipt> replay(UUID actor, UUID key, String fingerprint) {
        return intentRepository.findByActorPublicIdAndOperationAndIdempotencyKey(
                actor, BillingConstants.LICENSE_ISSUE_OPERATION, key).map(intent -> receipt(intent, fingerprint));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LicenseIssueReceipt issue(LicenseCode code, UUID key, String fingerprint) {
        Optional<LicenseIssueIntent> existing = intentRepository.findByActorPublicIdAndOperationAndIdempotencyKey(
                code.getIssuedBy(), BillingConstants.LICENSE_ISSUE_OPERATION, key);
        if (existing.isPresent()) return receipt(existing.get(), fingerprint);

        Plan plan = planRepository.findByPublicIdForUpdate(code.getPlanId())
                .filter(p -> !p.isDeleted()).orElseThrow(PlanNotFoundException::new);
        // A winning request may have committed while this request waited for the Plan lock.
        existing = intentRepository.findByActorPublicIdAndOperationAndIdempotencyKey(
                code.getIssuedBy(), BillingConstants.LICENSE_ISSUE_OPERATION, key);
        if (existing.isPresent()) return receipt(existing.get(), fingerprint);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
        }
        if (plan.getType() != PlanType.EXAM_PACKAGE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_EXAM_PLAN_REQUIRED);
        }
        PlanService.validateExamBenefits(plan.getDurationDays(), plan.getMaxStudentsPerSession(), plan.getExtraStudentSlots());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (code.getCodeExpiresAt() != null && !code.getCodeExpiresAt().isAfter(now)) {
            throw new LicenseCodeException(HttpStatus.UNPROCESSABLE_ENTITY, BillingConstants.LICENSE_CODE_EXPIRY_INVALID);
        }
        LicenseCode saved = licenseCodeRepository.saveAndFlush(code);
        intentRepository.saveAndFlush(LicenseIssueIntent.create(code.getIssuedBy(),
                BillingConstants.LICENSE_ISSUE_OPERATION, key, fingerprint, saved.getPublicId()));
        return receipt(saved, now, false);
    }

    private LicenseIssueReceipt receipt(LicenseIssueIntent intent, String fingerprint) {
        if (!intent.getPayloadFingerprint().equals(fingerprint)) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REUSED);
        }
        LicenseCode code = licenseCodeRepository.findByPublicId(intent.getLicenseCodePublicId())
                .filter(result -> !result.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.GONE,
                        BillingConstants.LICENSE_CODE_ISSUE_RESULT_MISSING));
        return receipt(code, clock.instant().truncatedTo(ChronoUnit.MICROS), true);
    }

    private LicenseIssueReceipt receipt(LicenseCode code, Instant now, boolean replayed) {
        return new LicenseIssueReceipt(code.getPublicId(), code.getPlanId(), code.getStatus().name(),
                LicenseCodeStateResolver.resolve(code, now).name(), code.getIssuedAt(), code.getCodeExpiresAt(), replayed);
    }

}
