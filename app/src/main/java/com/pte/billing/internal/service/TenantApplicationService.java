package com.pte.billing.internal.service;

import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.application.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.application.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.exception.application.ApplicationNotPendingException;
import com.pte.billing.internal.exception.license.RequestedCodeAlreadyUsedException;
import com.pte.billing.internal.exception.application.RequestedNameAlreadyUsedException;
import com.pte.billing.internal.exception.application.RequestedTaxCodeAlreadyUsedException;
import com.pte.billing.internal.exception.application.TenantApplicationNotFoundException;
import com.pte.billing.internal.exception.application.TenantApplicationValidationException;
import com.pte.billing.internal.mapper.TenantApplicationMapper;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.IdentityService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Organization;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tenant self-registration and platform review (ADR-006 §1). Depends OUT on
 * {@code tenancy} and {@code identity} only — never the other way. See
 * plan.md's module-boundary check for this phase.
 */
@Service
public class TenantApplicationService {

    private final TenancyService tenancyService;
    private final IdentityService identityService;
    private final PlatformSettingService platformSettingService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public TenantApplicationService(
            TenancyService tenancyService, IdentityService identityService,
            PlatformSettingService platformSettingService, ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService) {
        this.tenancyService = tenancyService;
        this.identityService = identityService;
        this.platformSettingService = platformSettingService;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    /** Compatibility constructor for existing focused tests and public submit wiring. */
    public TenantApplicationService(
            TenancyService tenancyService, IdentityService identityService,
            PlatformSettingService platformSettingService, ApplicationEventPublisher eventPublisher) {
        this(tenancyService, identityService, platformSettingService, eventPublisher, null);
    }

    /**
     * Public, no auth — see class javadoc and {@code SecurityConfig.PUBLIC_PATHS}.
     */
    @Transactional
    public void submit(SubmitApplicationRequest request) {
        String organizationName = request.orgName().trim();
        if (tenancyService.existsByName(organizationName)) {
            throw new RequestedNameAlreadyUsedException();
        }
        if (tenancyService.existsByCode(request.requestedCode())) {
            throw new RequestedCodeAlreadyUsedException();
        }
        Organization saved = tenancyService.submitApplication(organizationName, request.orgType(),
                request.requestedCode(), request.taxCode().trim(), request.contactEmail(), request.contactPhone());
        eventPublisher.publishEvent(new TenantApplicationSubmittedEvent(
                saved.getPublicId(), saved.getName(), saved.getCode(), saved.getContactEmail()));
    }

    @Transactional(readOnly = true)
    public List<TenantApplicationResponse> list() {
        return list(null);
    }

    @Transactional(readOnly = true)
    public List<TenantApplicationResponse> list(CurrentUser caller) {
        requireOperation(caller, PlatformOperation.APPLICATION_READ, BillingConstants.AUDIT_APPLICATION_READ);
        return tenancyService.listApplications().stream()
                .map(TenantApplicationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantApplicationResponse get(UUID applicationPublicId) {
        return get(applicationPublicId, null);
    }

    @Transactional(readOnly = true)
    public TenantApplicationResponse get(UUID applicationPublicId, CurrentUser caller) {
        requireOperation(caller, PlatformOperation.APPLICATION_READ, BillingConstants.AUDIT_APPLICATION_READ);
        return tenancyService.findApplication(applicationPublicId)
                .map(TenantApplicationMapper::toResponse)
                .orElseThrow(TenantApplicationNotFoundException::new);
    }

    /**
     * One transaction end to end: application status, the new tenant, and its
     * first HOST_ADMIN either all commit or none do. A failure creating the
     * HOST_ADMIN must not leave an orphaned tenant with no one able to log in.
     */
    @Transactional
    public void approve(UUID applicationPublicId, CurrentUser caller) {
        requireOperation(caller, PlatformOperation.APPLICATION_REVIEW, BillingConstants.AUDIT_APPLICATION_REVIEW);
        Organization application = findPendingForUpdate(applicationPublicId);
        try {
            int freeStudentLimit = platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY);
            Organization tenant = tenancyService.approveApplication(application, caller.userId(), freeStudentLimit);
            HostAdminCreated hostAdmin = identityService.createHostAdmin(
                    tenant.getPublicId(), application.getContactEmail());
            audit(caller, BillingConstants.AUDIT_APPLICATION_REVIEW, application.getPublicId(),
                    BillingConstants.AUDIT_APPLICATION_APPROVED_SUMMARY);
            eventPublisher.publishEvent(new TenantApplicationApprovedEvent(
                    application.getPublicId(), tenant.getPublicId(), application.getName(), tenant.getCode(),
                    application.getContactEmail(), hostAdmin.user().getUsername(), hostAdmin.generatedPassword()));
        } catch (DataIntegrityViolationException ex) {
            throw translateApprovalConflict(ex);
        }
    }

    /**
     * Rejecting frees the requested code immediately — the partial unique index
     * only covers PENDING rows.
     */
    @Transactional
    public TenantApplicationResponse reject(UUID applicationPublicId, RejectApplicationRequest request,
            CurrentUser caller) {
        requireOperation(caller, PlatformOperation.APPLICATION_REVIEW, BillingConstants.AUDIT_APPLICATION_REVIEW);
        Organization application = findPendingForUpdate(applicationPublicId);
        String reason = normalizeRejectReason(request);
        tenancyService.rejectApplication(application, caller.userId(), reason);
        audit(caller, BillingConstants.AUDIT_APPLICATION_REVIEW, application.getPublicId(),
                BillingConstants.AUDIT_APPLICATION_REJECTED_SUMMARY);
        eventPublisher.publishEvent(new TenantApplicationRejectedEvent(
                application.getPublicId(), application.getName(), application.getCode(),
                application.getContactEmail(), application.getRejectReason()));
        return TenantApplicationMapper.toResponse(application);
    }

    private Organization findPendingForUpdate(UUID applicationPublicId) {
        Organization application = tenancyService.findApplicationForUpdate(applicationPublicId)
                .orElseThrow(TenantApplicationNotFoundException::new);
        if (!application.isPending()) {
            throw new ApplicationNotPendingException();
        }
        return application;
    }

    private String normalizeRejectReason(RejectApplicationRequest request) {
        if (request == null || request.reason() == null) {
            throw new TenantApplicationValidationException(BillingConstants.REJECT_REASON_REQUIRED);
        }
        String reason = request.reason().trim();
        if (reason.isEmpty()) {
            throw new TenantApplicationValidationException(BillingConstants.REJECT_REASON_REQUIRED);
        }
        if (reason.length() > 500) {
            throw new TenantApplicationValidationException(BillingConstants.REJECT_REASON_MAX);
        }
        return reason;
    }

    private RuntimeException translateApprovalConflict(DataIntegrityViolationException ex) {
        if (containsConstraint(ex, "uq_org_code_live")) {
            return new RequestedCodeAlreadyUsedException();
        }
        if (containsConstraint(ex, "uq_org_name_live")) {
            return new RequestedNameAlreadyUsedException();
        }
        if (containsConstraint(ex, "uq_org_tax_code_approved")) {
            return new RequestedTaxCodeAlreadyUsedException();
        }
        return ex;
    }

    private boolean containsConstraint(Throwable throwable, String constraint) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private void requireOperation(CurrentUser caller, PlatformOperation operation, String action) {
        // Null is retained only for legacy in-process compatibility overloads;
        // HTTP controllers always resolve an authenticated platform caller.
        if (caller == null) {
            return;
        }
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, BillingConstants.TENANT_APPLICATION_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, action);
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    private void audit(CurrentUser caller, String action, UUID aggregateId, String summary) {
        if (auditLogService != null && caller != null) {
            auditLogService.record(caller, BillingConstants.TENANT_APPLICATION_AGGREGATE,
                    aggregateId.toString(), action, summary);
        }
    }
}
