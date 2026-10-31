package com.pte.billing.internal.service.license;

import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.license.GrantLicenseRequest;
import com.pte.billing.internal.dto.request.license.PreviewLicenseGrantRequest;
import com.pte.billing.internal.dto.response.LicenseGrantPreviewResponse;
import com.pte.billing.internal.dto.response.LicenseGrantPreviewResponse.GrantHostView;
import com.pte.billing.internal.dto.response.LicenseGrantResponse;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin grant of a plan to one organization under a contract. One transaction activates the plan through the
 * shared activation path and records a license code bound to that organization, already redeemed, so the host
 * does nothing and the existing code revocation still corrects a mistaken grant.
 */
@Service
public class LicenseGrantService {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final LicenseCodeRepository licenseCodeRepository;
    private final ServicePlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionActivationService activationService;
    private final LicenseCodeGenerator codeGenerator;
    private final IdempotencyKeyService idempotencyKeyService;
    private final TenancyService tenancyService;
    private final IdentityService identityService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;
    private final LicenseCodeAccessPolicy accessPolicy;
    private final Clock clock;

    public LicenseGrantService(LicenseCodeRepository licenseCodeRepository, ServicePlanRepository planRepository,
            SubscriptionRepository subscriptionRepository, SubscriptionActivationService activationService,
            LicenseCodeGenerator codeGenerator, IdempotencyKeyService idempotencyKeyService,
            TenancyService tenancyService, IdentityService identityService, AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher, JsonMapper jsonMapper, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.activationService = activationService;
        this.codeGenerator = codeGenerator;
        this.idempotencyKeyService = idempotencyKeyService;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = jsonMapper;
        this.accessPolicy = new LicenseCodeAccessPolicy(auditLogService, tenancyService);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LicenseGrantPreviewResponse preview(PreviewLicenseGrantRequest request, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_ISSUE);
        if (!tenancyService.isActiveTenant(request.tenantId())) {
            throw tenantNotActive();
        }
        List<GrantHostView> hosts = identityService.findActiveByTenantIdAndRole(request.tenantId(), Role.HOST_ADMIN)
                .stream()
                .map(user -> new GrantHostView(user.getUsername(), user.getEmail(), user.getFullName()))
                .toList();
        if (request.planId() == null) {
            return new LicenseGrantPreviewResponse(request.tenantId(), null, null, null, hosts,
                    null, null, null, null, null, null, null);
        }
        ServicePlan plan = activePlan(planRepository.findByPublicId(request.planId()).orElse(null));
        if (plan.getType() == PlanType.STUDENT_CAPACITY) {
            return new LicenseGrantPreviewResponse(request.tenantId(), plan.getPublicId(), plan.getName(),
                    plan.getType().name(), hosts, null, null, null, null, plan.getExtraStudentSlots(), null, null);
        }
        Instant defaultStart = activationService.resolveDefaultStart(request.tenantId(), plan.getPublicId());
        Instant startsAt = request.startsAt() == null ? defaultStart : activationService.resolveStart(request.startsAt());
        Subscription renewed = latestLane(request.tenantId(), plan.getPublicId());
        return new LicenseGrantPreviewResponse(request.tenantId(), plan.getPublicId(), plan.getName(),
                plan.getType().name(), hosts, defaultStart, startsAt,
                startsAt.plus(Duration.ofDays(plan.getDurationDays())), plan.getMaxStudentsPerSession(), null,
                renewed == null ? null : mask(renewed.getLicenseKey()),
                renewed == null ? null : renewed.getExpiresAt());
    }

    @Transactional
    public LicenseGrantResponse grant(GrantLicenseRequest request, UUID key, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_ISSUE);
        // The tenant row lock serializes grants for one organization, so two concurrent grants cannot both see
        // "no current lane" and start on overlapping days.
        if (!tenancyService.lockActiveTenant(request.tenantId())) {
            throw tenantNotActive();
        }
        String contractReference = request.contractReference().trim();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String idempotencyKey = BillingConstants.LICENSE_GRANT_KEY_PREFIX + caller.userId()
                + BillingConstants.LICENSE_ISSUE_KEY_SEPARATOR + key;
        IdempotencyDecision decision = idempotencyKeyService.begin(IdempotencyScope.LICENSE_ISSUE, idempotencyKey,
                null, fingerprint(request, contractReference), now);
        switch (decision.outcome()) {
            case REPLAY:
                return replay(decision.resultJson());
            case CONFLICT:
                throw new LicenseCodeException(HttpStatus.CONFLICT,
                        BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REUSED);
            default:
                break;
        }

        ServicePlan plan = activePlan(planRepository.findByPublicIdForUpdate(request.planId()).orElse(null));
        Instant requestedStart = plan.getType() == PlanType.EXAM_PACKAGE
                ? (request.startsAt() == null
                        ? activationService.resolveDefaultStart(request.tenantId(), plan.getPublicId())
                        : request.startsAt())
                : null;
        SubscriptionActivationResponse activation = activationService.activate(request.tenantId(), plan,
                ActivationSource.LICENSE_CODE, requestedStart);

        LicenseCode licenseCode = LicenseCode.grant(uniqueCode(), plan.getPublicId(), caller.userId(), now,
                request.tenantId(), requestedStart, contractReference, note);
        licenseCode.markRedeemed(request.tenantId(), now);
        boolean examPackage = activation.kind() == SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION;
        if (examPackage) {
            licenseCode.linkSubscription(activation.targetPublicId());
        }
        LicenseCode saved = licenseCodeRepository.saveAndFlush(licenseCode);

        eventPublisher.publishEvent(new CommercialOutcomeConfirmedEvent(
                request.tenantId(),
                plan.getPublicId(),
                examPackage ? CommercialOutcomeType.EXAM_PACKAGE : CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.LICENSE_CODE,
                examPackage ? CommercialActivationTarget.SUBSCRIPTION : CommercialActivationTarget.QUOTA,
                activation.targetPublicId(),
                activation.grantedStudentSlots(),
                activation.startsAt()));
        auditLogService.record(caller, BillingConstants.LICENSE_CODE_AGGREGATE, saved.getPublicId().toString(),
                BillingConstants.AUDIT_LICENSE_GRANT, BillingConstants.LICENSE_GRANT_AUDIT_SUMMARY.formatted(
                        request.tenantId(), plan.getPublicId(), contractReference, activation.startsAt()));
        idempotencyKeyService.complete(IdempotencyScope.LICENSE_ISSUE, idempotencyKey, null,
                jsonMapper.writeValueAsString(Map.of(BillingConstants.LICENSE_ISSUE_RESULT_FIELD,
                        saved.getPublicId().toString())));
        return response(saved, plan, activation.startsAt(), activation.expiresAt(), activation.licenseKey(),
                activation.grantedStudentSlots(), false);
    }

    private LicenseGrantResponse replay(String resultJson) {
        UUID codeId = UUID.fromString(jsonMapper.readTree(resultJson)
                .path(BillingConstants.LICENSE_ISSUE_RESULT_FIELD).asString());
        LicenseCode code = licenseCodeRepository.findByPublicId(codeId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.GONE,
                        BillingConstants.LICENSE_CODE_ISSUE_RESULT_MISSING));
        ServicePlan plan = planRepository.findByPublicId(code.getPlanId()).orElseThrow(PlanNotFoundException::new);
        Subscription subscription = code.getSubscriptionId() == null ? null
                : subscriptionRepository.findByPublicId(code.getSubscriptionId()).orElse(null);
        return response(code, plan,
                subscription == null ? null : subscription.getStartsAt(),
                subscription == null ? null : subscription.getExpiresAt(),
                subscription == null ? null : subscription.getLicenseKey(),
                plan.getType() == PlanType.STUDENT_CAPACITY ? plan.getExtraStudentSlots() : null, true);
    }

    private LicenseGrantResponse response(LicenseCode code, ServicePlan plan, Instant startsAt, Instant expiresAt,
            String licenseKey, Integer grantedSlots, boolean replayed) {
        boolean examPackage = plan.getType() == PlanType.EXAM_PACKAGE;
        return new LicenseGrantResponse(code.getPublicId(), code.getBoundTenantId(), plan.getPublicId(),
                examPackage ? SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION.name()
                        : SubscriptionActivationResponse.ActivationKind.STUDENT_CAPACITY.name(),
                code.getSubscriptionId(), mask(licenseKey), startsAt, expiresAt,
                examPackage ? plan.getMaxStudentsPerSession() : null,
                examPackage ? null : grantedSlots, code.getContractReference(), replayed);
    }

    /** The latest still-active lane of the plan for this tenant, which a default-start grant continues. */
    private Subscription latestLane(UUID tenantId, UUID planId) {
        Instant now = clock.instant();
        return subscriptionRepository.findRenewableForPlan(tenantId, planId, SubscriptionStatus.ACTIVE, now)
                .stream().findFirst().orElse(null);
    }

    /** Pre-checks uniqueness: a constraint violation would leave this single transaction rollback-only. */
    private String uniqueCode() {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate();
            if (!licenseCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new LicenseCodeException(HttpStatus.SERVICE_UNAVAILABLE, BillingConstants.LICENSE_CODE_ISSUE_RETRYABLE);
    }

    private ServicePlan activePlan(ServicePlan plan) {
        if (plan == null || plan.isDeleted()) {
            throw new PlanNotFoundException();
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
        }
        return plan;
    }

    private LicenseCodeException tenantNotActive() {
        return new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_GRANT_TENANT_NOT_ACTIVE);
    }

    private String fingerprint(GrantLicenseRequest request, String contractReference) {
        String canonical = request.tenantId() + "\n" + request.planId() + "\n"
                + (request.startsAt() == null ? "default" : request.startsAt().toString()) + "\n" + contractReference;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String mask(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4 ? "••••" : "•••• " + value.substring(value.length() - 4);
    }
}
