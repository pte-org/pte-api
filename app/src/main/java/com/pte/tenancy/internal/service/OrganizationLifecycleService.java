package com.pte.tenancy.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.tenancy.TenantSuspensionDurationProvider;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.TenantSuspensionImpactProvider;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.SuspendTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.exception.TenantCodeAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantTaxCodeAlreadyUsedException;
import com.pte.tenancy.internal.mapper.OrganizationMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Organization governance: platform onboarding, the application workflow (submit, approve, reject), suspension
 * and branding. Authorization for the application workflow lives with the caller (billing); platform operations
 * on approved organizations are authorized here.
 */
@Service
public class OrganizationLifecycleService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationOperationAudit operationAudit;
    private final OrganizationSuspension suspension;
    private final Clock clock;

    public OrganizationLifecycleService(OrganizationRepository organizationRepository) {
        this(organizationRepository, null, null, null, Clock.systemUTC());
    }

    public OrganizationLifecycleService(OrganizationRepository organizationRepository,
            AuditLogService auditLogService) {
        this(organizationRepository, auditLogService, null, null, Clock.systemUTC());
    }

    @Autowired
    public OrganizationLifecycleService(OrganizationRepository organizationRepository,
            AuditLogService auditLogService, TenantSuspensionDurationProvider suspensionDurationProvider,
            TenantSuspensionImpactProvider suspensionImpactProvider, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.operationAudit = new OrganizationOperationAudit(auditLogService);
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.suspension = new OrganizationSuspension(organizationRepository, operationAudit,
                suspensionDurationProvider, suspensionImpactProvider, this.clock);
    }

    @Transactional
    public OrganizationResponse onboard(OnboardTenantRequest request) {
        return onboard(request, null);
    }

    @Transactional
    public OrganizationResponse onboard(OnboardTenantRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_ONBOARD, TenancyConstants.OPERATION_TENANT_ONBOARD);
        assertIdentityFree(request.code(), request.name(), request.taxCode().trim(), null);
        Organization organization = newOrganization(request.name(), request.organizationType(), request.code(),
                request.taxCode().trim());
        organization.setPackageName(request.packageName());
        organization.setStudentLimit(request.studentLimit());
        organization.approve(null, clock.instant());
        Organization saved = organizationRepository.save(organization);
        audit(caller, TenancyConstants.EVENT_TENANT_ONBOARDED, saved.getPublicId(),
                TenancyConstants.AUDIT_TENANT_ONBOARDED_SUMMARY);
        return OrganizationMapper.toResponse(saved);
    }

    /**
     * Records a public onboarding application as a {@code PENDING} organization. Joins the caller's transaction.
     * Code and name are reserved from this moment; the tax code is only enforced when the application is approved.
     */
    public Organization submitApplication(String name, String organizationType, String code, String taxCode,
            String contactEmail, String contactPhone) {
        Organization organization = newOrganization(name, organizationType, code, taxCode);
        organization.setContactEmail(contactEmail);
        organization.setContactPhone(contactPhone);
        return organizationRepository.saveAndFlush(organization);
    }

    /**
     * Approves a pending application inside the caller's transaction (a failure creating the first HOST_ADMIN
     * right after this must roll the approval back too). Re-validates tax-code uniqueness because time passes
     * between submission and approval.
     */
    public Organization approveApplication(Organization application, UUID reviewerPublicId, int studentLimit) {
        if (organizationRepository.existsByTaxCodeAndStatusInAndPublicIdNot(application.getTaxCode(),
                List.of(OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED), application.getPublicId())) {
            throw new TenantTaxCodeAlreadyUsedException();
        }
        application.setStudentLimit(studentLimit);
        application.approve(reviewerPublicId, clock.instant());
        return organizationRepository.saveAndFlush(application);
    }

    public Organization rejectApplication(Organization application, UUID reviewerPublicId, String reason) {
        application.reject(reviewerPublicId, clock.instant(), reason);
        return organizationRepository.saveAndFlush(application);
    }

    private Organization newOrganization(String name, String organizationType, String code, String taxCode) {
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(name);
        organization.setOrganizationType(organizationType);
        organization.setTaxCode(taxCode);
        organization.setFacilityType(FacilityType.MAIN);
        return organization;
    }

    private void assertIdentityFree(String code, String name, String taxCode, UUID excludedPublicId) {
        if (organizationRepository.existsByCodeAndStatusNot(code, OrganizationStatus.REJECTED)) {
            throw new TenantCodeAlreadyUsedException();
        }
        if (organizationRepository.existsByNameAndStatusNot(name, OrganizationStatus.REJECTED)) {
            throw new TenantNameAlreadyUsedException();
        }
        if (organizationRepository.existsByTaxCodeAndStatusInAndPublicIdNot(taxCode,
                List.of(OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED),
                excludedPublicId == null ? new UUID(0L, 0L) : excludedPublicId)) {
            throw new TenantTaxCodeAlreadyUsedException();
        }
    }

    @Transactional
    public OrganizationResponse suspend(UUID publicId) {
        return suspend(publicId, null, null);
    }

    @Transactional
    public OrganizationResponse suspend(UUID publicId, CurrentUser caller) {
        return suspend(publicId, null, caller);
    }

    @Transactional
    public OrganizationResponse suspend(UUID publicId, Integer requestedDays) {
        return suspend(publicId, requestedDays, null);
    }

    @Transactional
    public OrganizationResponse suspend(UUID publicId, Integer requestedDays, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_LIFECYCLE, TenancyConstants.OPERATION_TENANT_SUSPEND);
        return suspension.suspend(publicId, requestedDays, caller);
    }

    /**
     * Confirms a live suspension preview before mutating state. The row lock keeps re-suspension idempotent and
     * makes the final status transition atomic with the digest check.
     */
    @Transactional
    public OrganizationResponse suspendWithConfirmation(UUID publicId, SuspendTenantRequest request,
            CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_LIFECYCLE, TenancyConstants.OPERATION_TENANT_SUSPEND);
        return suspension.suspendWithConfirmation(publicId, request, caller);
    }

    @Transactional
    public OrganizationResponse reactivate(UUID publicId) {
        return reactivate(publicId, null);
    }

    @Transactional
    public OrganizationResponse reactivate(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_LIFECYCLE, TenancyConstants.OPERATION_TENANT_REACTIVATE);
        Organization organization = organizationRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (organization.getStatus() == OrganizationStatus.ACTIVE) {
            return OrganizationMapper.toResponse(organization);
        }
        organization.reactivate();
        audit(caller, TenancyConstants.EVENT_TENANT_REACTIVATED, publicId,
                TenancyConstants.AUDIT_TENANT_REACTIVATED_SUMMARY);
        return OrganizationMapper.toResponse(organization);
    }

    @Transactional(readOnly = true)
    public TenantSuspensionImpact suspensionImpact(UUID publicId, Integer requestedDays) {
        return suspension.suspensionImpact(publicId, requestedDays);
    }

    @Transactional
    public OrganizationResponse updateBranding(UUID publicId, UpdateBrandingRequest request) {
        return updateBranding(publicId, request, null);
    }

    @Transactional
    public OrganizationResponse updateBranding(UUID publicId, UpdateBrandingRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_BRANDING, TenancyConstants.OPERATION_TENANT_BRANDING);
        Organization organization = organizationRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        organization.updateBranding(request.logoUrl(), request.primaryColor());
        audit(caller, TenancyConstants.EVENT_TENANT_BRANDING_UPDATED, publicId,
                TenancyConstants.AUDIT_TENANT_BRANDING_UPDATED_SUMMARY);
        return OrganizationMapper.toResponse(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID publicId) {
        return get(publicId, null);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_READ, PlatformOperation.TENANT_READ.name());
        return OrganizationMapper.toResponse(organizationRepository.findByPublicId(publicId)
                .filter(organization -> !organization.isPending() && organization.getStatus() != OrganizationStatus.REJECTED)
                .orElseThrow(TenantNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list() {
        return list(null);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_READ, PlatformOperation.TENANT_READ.name());
        return organizationRepository.findAllApproved().stream().map(OrganizationMapper::toResponse).toList();
    }

    private void require(CurrentUser caller, PlatformOperation operation, String action) {
        operationAudit.require(caller, operation, action);
    }

    private void audit(CurrentUser caller, String action, UUID publicId, String summary) {
        operationAudit.audit(caller, action, publicId, summary);
    }
}
