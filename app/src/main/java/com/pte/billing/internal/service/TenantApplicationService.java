package com.pte.billing.internal.service;

import com.pte.billing.domain.TenantApplication;
import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.ApproveApplicationResponse;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.exception.ApplicationNotPendingException;
import com.pte.billing.internal.exception.RequestedCodeAlreadyUsedException;
import com.pte.billing.internal.exception.TenantApplicationNotFoundException;
import com.pte.billing.internal.mapper.TenantApplicationMapper;
import com.pte.billing.internal.repository.TenantApplicationRepository;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.internal.service.IdentityService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Tenant;
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

    public TenantApplicationService(TenantApplicationRepository applicationRepository,
            TenancyService tenancyService, IdentityService identityService) {
        this.applicationRepository = applicationRepository;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
    }

    /**
     * Public, no auth — see class javadoc and {@code SecurityConfig.PUBLIC_PATHS}.
     */
    @Transactional
    public TenantApplicationResponse submit(SubmitApplicationRequest request) {
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
        application.setTaxCode(request.taxCode());

        TenantApplication saved = applicationRepository.saveAndFlush(application);
        return TenantApplicationMapper.toResponse(saved);
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
    public ApproveApplicationResponse approve(UUID applicationPublicId, CurrentUser caller) {
        TenantApplication application = findPending(applicationPublicId);

        Tenant tenant = tenancyService.createTenant(application.getOrgName(), application.getOrgType(),
                application.getRequestedCode(), BillingConstants.TEMPORARY_FREE_STUDENT_LIMIT);
        HostAdminCreated hostAdmin = identityService.createHostAdmin(
                tenant.getPublicId(), application.getContactEmail());

        application.approve(caller.userId());

        return new ApproveApplicationResponse(
                tenant.getPublicId(),
                tenant.getCode(),
                hostAdmin.user().getPublicId(),
                hostAdmin.user().getUsername(),
                hostAdmin.generatedPassword());
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
