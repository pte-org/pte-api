package com.pte.billing.internal.service;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.TenantApplicationApprovedEvent;
import com.pte.billing.TenantApplicationRejectedEvent;
import com.pte.billing.TenantApplicationSubmittedEvent;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.exception.ApplicationNotPendingException;
import com.pte.billing.internal.exception.RequestedCodeAlreadyUsedException;
import com.pte.billing.internal.exception.TenantApplicationNotFoundException;
import com.pte.billing.internal.mapper.TenantApplicationMapper;
import com.pte.billing.internal.repository.TenantApplicationRepository;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.IdentityService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Tenant;
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

    private final TenantApplicationRepository applicationRepository;
    private final TenancyService tenancyService;
    private final IdentityService identityService;
    private final PlatformSettingService platformSettingService;
    private final ApplicationEventPublisher eventPublisher;

    public TenantApplicationService(TenantApplicationRepository applicationRepository,
            TenancyService tenancyService, IdentityService identityService,
            PlatformSettingService platformSettingService, ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
        this.platformSettingService = platformSettingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Public, no auth — see class javadoc and {@code SecurityConfig.PUBLIC_PATHS}.
     */
    @Transactional
    public void submit(SubmitApplicationRequest request) {
        if (tenancyService.existsByCode(request.requestedCode())
                || applicationRepository.existsByRequestedCodeAndStatus(
                        request.requestedCode(), TenantApplicationStatus.PENDING)) {
            throw new RequestedCodeAlreadyUsedException();
        }

        TenantApplication application = new TenantApplication();
        application.setOrgName(request.orgName());
        application.setOrgType(request.orgType());
        application.setRequestedCode(request.requestedCode());
        application.setContactEmail(request.contactEmail());
        application.setContactPhone(request.contactPhone());
        application.setTaxCode(request.taxCode().trim());

        TenantApplication saved = applicationRepository.saveAndFlush(application);
        eventPublisher.publishEvent(new TenantApplicationSubmittedEvent(
                saved.getPublicId(), saved.getOrgName(), saved.getRequestedCode(), saved.getContactEmail()));
    }

    @Transactional(readOnly = true)
    public List<TenantApplicationResponse> list() {
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TenantApplicationMapper::toResponse)
                .toList();
    }

    /**
     * One transaction end to end: application status, the new tenant, and its
     * first HOST_ADMIN either all commit or none do. A failure creating the
     * HOST_ADMIN must not leave an orphaned tenant with no one able to log in.
     */
    @Transactional
    public void approve(UUID applicationPublicId, CurrentUser caller) {
        TenantApplication application = findPending(applicationPublicId);
        int freeStudentLimit = platformSettingService.getInteger(BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY);

        Tenant tenant = tenancyService.createTenant(application.getOrgName(), application.getOrgType(),
                application.getRequestedCode(), application.getTaxCode(), freeStudentLimit);
        HostAdminCreated hostAdmin = identityService.createHostAdmin(
                tenant.getPublicId(), application.getContactEmail());

        application.approve(caller.userId());
        eventPublisher.publishEvent(new TenantApplicationApprovedEvent(
                application.getPublicId(), tenant.getPublicId(), application.getOrgName(), tenant.getCode(),
                application.getContactEmail(), hostAdmin.user().getUsername(), hostAdmin.generatedPassword()));
    }

    /**
     * Rejecting frees the requested code immediately — the partial unique index
     * only covers PENDING rows.
     */
    @Transactional
    public TenantApplicationResponse reject(UUID applicationPublicId, RejectApplicationRequest request,
            CurrentUser caller) {
        TenantApplication application = findPending(applicationPublicId);
        application.reject(caller.userId(), request.reason());
        eventPublisher.publishEvent(new TenantApplicationRejectedEvent(
                application.getPublicId(), application.getOrgName(), application.getRequestedCode(),
                application.getContactEmail(), application.getRejectReason()));
        return TenantApplicationMapper.toResponse(application);
    }

    private TenantApplication findPending(UUID applicationPublicId) {
        TenantApplication application = applicationRepository.findByPublicId(applicationPublicId)
                .orElseThrow(TenantApplicationNotFoundException::new);
        if (application.getStatus() != TenantApplicationStatus.PENDING) {
            throw new ApplicationNotPendingException();
        }
        return application;
    }
}
