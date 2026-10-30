package com.pte.billing.internal.service.license;

import com.pte.billing.internal.service.PlanService;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Isolates license-code inserts so a unique-code collision can be retried. Issuance dedupe and the replay receipt
 * live in the shared idempotency store (scope {@code LICENSE_ISSUE}); the stored result is the issued code's id,
 * so no bearer code or request body is kept.
 */
@Service
public class LicenseCodePersistenceService {

    private final LicenseCodeRepository licenseCodeRepository;
    private final ServicePlanRepository planRepository;
    private final IdempotencyKeyService idempotencyKeyService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public LicenseCodePersistenceService(LicenseCodeRepository licenseCodeRepository,
            ServicePlanRepository planRepository, IdempotencyKeyService idempotencyKeyService, JsonMapper jsonMapper, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.idempotencyKeyService = idempotencyKeyService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LicenseIssueReceipt issue(LicenseCode code, UUID key, String fingerprint) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String idempotencyKey = code.getIssuedBy() + BillingConstants.LICENSE_ISSUE_KEY_SEPARATOR + key;
        IdempotencyDecision decision = idempotencyKeyService.begin(
                IdempotencyScope.LICENSE_ISSUE, idempotencyKey, null, fingerprint, now);
        switch (decision.outcome()) {
            case REPLAY:
                return replayedReceipt(decision.resultJson(), now);
            case CONFLICT:
                throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REUSED);
            default:
                break;
        }

        ServicePlan plan = planRepository.findByPublicIdForUpdate(code.getPlanId())
                .filter(p -> !p.isDeleted()).orElseThrow(PlanNotFoundException::new);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
        }
        if (plan.getType() != PlanType.EXAM_PACKAGE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_EXAM_PLAN_REQUIRED);
        }
        PlanService.validateExamBenefits(plan.getDurationDays(), plan.getMaxStudentsPerSession(), plan.getExtraStudentSlots());
        if (code.getCodeExpiresAt() != null && !code.getCodeExpiresAt().isAfter(now)) {
            throw new LicenseCodeException(HttpStatus.UNPROCESSABLE_ENTITY, BillingConstants.LICENSE_CODE_EXPIRY_INVALID);
        }
        LicenseCode saved = licenseCodeRepository.saveAndFlush(code);
        idempotencyKeyService.complete(IdempotencyScope.LICENSE_ISSUE, idempotencyKey, null,
                jsonMapper.writeValueAsString(Map.of(BillingConstants.LICENSE_ISSUE_RESULT_FIELD,
                        saved.getPublicId().toString())));
        return receipt(saved, now, false);
    }

    private LicenseIssueReceipt replayedReceipt(String resultJson, Instant now) {
        UUID issuedCodePublicId = UUID.fromString(
                jsonMapper.readTree(resultJson).path(BillingConstants.LICENSE_ISSUE_RESULT_FIELD).asText());
        LicenseCode code = licenseCodeRepository.findByPublicId(issuedCodePublicId)
                .filter(result -> !result.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.GONE,
                        BillingConstants.LICENSE_CODE_ISSUE_RESULT_MISSING));
        return receipt(code, now, true);
    }

    private LicenseIssueReceipt receipt(LicenseCode code, Instant now, boolean replayed) {
        return new LicenseIssueReceipt(code.getPublicId(), code.getPlanId(), code.getStatus().name(),
                LicenseCodeStateResolver.resolve(code, now).name(), code.getIssuedAt(), code.getCodeExpiresAt(), replayed);
    }

}
