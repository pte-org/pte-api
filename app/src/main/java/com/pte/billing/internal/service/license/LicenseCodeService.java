package com.pte.billing.internal.service.license;

import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.LicenseCodeRevealResponse;
import com.pte.billing.internal.dto.request.license.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.web.PagedResult;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

/** Issues bearer codes and funnels redemption through the single activation path. */
@Service
public class LicenseCodeService {

    private final LicenseCodeRepository licenseCodeRepository;
    private final AuditLogService auditLogService;
    private final LicenseCodeAccessPolicy accessPolicy;
    private final LicenseCodeIssuer issuer;
    private final LicenseCodeAdminQuery adminQuery;
    private final LicenseCodeRevoker revoker;
    private final LicenseCodeRedeemer redeemer;

    @Autowired
    public LicenseCodeService(LicenseCodeRepository licenseCodeRepository, ServicePlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            LicenseCodePersistenceService licenseCodePersistenceService,
            LicenseCodeGenerator licenseCodeGenerator,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher, AuditLogService auditLogService,
            TenancyService tenancyService, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.auditLogService = auditLogService;
        this.accessPolicy = new LicenseCodeAccessPolicy(auditLogService, tenancyService);
        this.issuer = new LicenseCodeIssuer(licenseCodePersistenceService, licenseCodeGenerator, auditLogService,
                clock);
        this.adminQuery = new LicenseCodeAdminQuery(licenseCodeRepository, planRepository, subscriptionRepository,
                tenancyService, clock);
        this.revoker = new LicenseCodeRevoker(licenseCodeRepository, subscriptionRepository, eventPublisher,
                new LicenseRevokeScopeResolver(licenseCodeRepository, planRepository, subscriptionRepository,
                        eventPublisher, clock),
                clock);
        this.redeemer = new LicenseCodeRedeemer(licenseCodeRepository, planRepository, subscriptionRepository,
                subscriptionActivationService, eventPublisher, clock);
    }

    public LicenseIssueReceipt issue(UUID planPublicId, Instant codeExpiresAt, UUID key, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_ISSUE);
        return issuer.issue(planPublicId, codeExpiresAt, key, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminLicenseCodeSummary> listForAdmin(Integer requestedPage, Integer requestedSize,
            String requestedStatus, UUID planId, UUID tenantId, CurrentUser caller) {
        return listForAdmin(requestedPage, requestedSize, requestedStatus, planId, tenantId, null, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminLicenseCodeSummary> listForAdmin(Integer requestedPage, Integer requestedSize,
            String requestedStatus, UUID planId, UUID tenantId, String requestedSearch, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_MASKED_READ);
        return adminQuery.listForAdmin(requestedPage, requestedSize, requestedStatus, planId, tenantId,
                requestedSearch);
    }

    @Transactional(readOnly = true)
    public AdminLicenseCodeSummary getForAdmin(UUID publicId, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_MASKED_READ);
        return adminQuery.getForAdmin(publicId);
    }

    @Transactional(readOnly = true)
    public AdminLicenseCodeSummary lookupForAdmin(String rawCode, CurrentUser caller) {
        accessPolicy.requirePlatformOperation(caller, PlatformOperation.LICENSE_MASKED_READ);
        return adminQuery.lookupForAdmin(rawCode);
    }

    /** Returns the bearer only after the caller has passed the platform-admin guard again. */
    @Transactional
    public LicenseCodeRevealResponse revealForAdmin(UUID publicId, CurrentUser caller) {
        accessPolicy.requirePlatformAdmin(caller);
        LicenseCode code = licenseCodeRepository.findByPublicId(publicId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        auditLogService.record(caller, BillingConstants.LICENSE_CODE_AGGREGATE, publicId.toString(),
                BillingConstants.LICENSE_CODE_REVEAL_AUDIT,
                BillingConstants.LICENSE_CODE_REVEAL_AUDIT_SUMMARY);
        return new LicenseCodeRevealResponse(code.getCode());
    }

    /** Authorizes the controlled retirement response for the old raw-list route. */
    public void ensureLegacyListAccess(CurrentUser caller) {
        accessPolicy.requirePlatformAdmin(caller);
    }

    @Transactional
    public LicenseRevokePreviewResponse previewRevoke(UUID publicId, CurrentUser caller) {
        accessPolicy.requirePlatformAdmin(caller);
        return revoker.previewRevoke(publicId, caller.userId());
    }

    @Transactional
    public LicenseRevokeResponse revoke(UUID publicId, ConfirmLicenseRevokeRequest request,
            CurrentUser caller) {
        accessPolicy.requirePlatformAdmin(caller);
        return revoker.revoke(publicId, request, caller.userId());
    }

    @Transactional
    public SubscriptionActivationResponse redeem(String rawCode, CurrentUser caller) {
        UUID tenantId = accessPolicy.requireHostTenant(caller);
        return redeemer.redeem(rawCode, tenantId);
    }
}
