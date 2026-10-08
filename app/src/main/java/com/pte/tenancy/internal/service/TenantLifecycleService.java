package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.exception.TenantCodeAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantTaxCodeAlreadyUsedException;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.response.TenantResponse;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.mapper.TenantMapper;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Tenant governance (onboard/suspend/read). */
@Service
public class TenantLifecycleService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    public TenantLifecycleService(TenantRepository tenantRepository) {
        this(tenantRepository, null);
    }

    @Autowired
    public TenantLifecycleService(TenantRepository tenantRepository, AuditLogService auditLogService) {
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TenantResponse onboard(OnboardTenantRequest request) {
        return onboard(request, null);
    }

    @Transactional
    public TenantResponse onboard(OnboardTenantRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_ONBOARD, TenancyConstants.OPERATION_TENANT_ONBOARD);
        if (tenantRepository.existsByCode(request.code())) {
            throw new TenantCodeAlreadyUsedException();
        }
        if (tenantRepository.existsByName(request.name())) {
            throw new TenantNameAlreadyUsedException();
        }
        if (tenantRepository.existsByTaxCode(request.taxCode().trim())) {
            throw new TenantTaxCodeAlreadyUsedException();
        }
        Tenant tenant = new Tenant();
        tenant.setCode(request.code());
        tenant.setName(request.name());
        tenant.setOrganizationType(request.organizationType());
        tenant.setTaxCode(request.taxCode().trim());
        tenant.setPackageName(request.packageName());
        tenant.setStudentLimit(request.studentLimit());
        addDefaultOrganization(tenant);
        Tenant saved = tenantRepository.save(tenant);
        audit(caller, TenancyConstants.EVENT_TENANT_ONBOARDED, saved.getPublicId(),
                TenancyConstants.AUDIT_TENANT_ONBOARDED_SUMMARY);
        return TenantMapper.toResponse(saved);
    }

    /**
     * Called by {@code billing.TenantApplicationService.approve()}, inside its
     * own transaction — this method has no {@code @Transactional} of its own
     * because it must join the caller's, not commit independently (a failure
     * creating the HOST_ADMIN right after this must roll the tenant back too).
     * Re-validates code/name uniqueness rather than trusting the caller's
     * earlier check: time passes between an application being submitted and
     * approved, and another onboarding could have taken the name/code meanwhile.
     */
    public Tenant createFromApplication(String name, String organizationType, String code, String taxCode,
            int studentLimit) {
        if (tenantRepository.existsByCode(code)) {
            throw new TenantCodeAlreadyUsedException();
        }
        if (tenantRepository.existsByName(name)) {
            throw new TenantNameAlreadyUsedException();
        }
        if (tenantRepository.existsByTaxCode(taxCode.trim())) {
            throw new TenantTaxCodeAlreadyUsedException();
        }
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(name);
        tenant.setOrganizationType(organizationType);
        tenant.setTaxCode(taxCode.trim());
        tenant.setPackageName("starter");
        tenant.setStudentLimit(studentLimit);
        addDefaultOrganization(tenant);
        return tenantRepository.save(tenant);
    }

    /**
     * A Host owns exactly one Organization. The child is created together with
     * the Tenant so no later branch-creation step is needed (or exposed).
     */
    private void addDefaultOrganization(Tenant tenant) {
        Organization organization = new Organization();
        organization.setName(tenant.getName());
        organization.setFacilityType(FacilityType.MAIN);
        tenant.addOrganization(organization);
    }

    @Transactional
    public TenantResponse suspend(UUID publicId) {
        return suspend(publicId, null);
    }

    @Transactional
    public TenantResponse suspend(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_LIFECYCLE, TenancyConstants.OPERATION_TENANT_SUSPEND);
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.suspend();
        audit(caller, TenancyConstants.EVENT_TENANT_SUSPENDED, publicId,
                TenancyConstants.AUDIT_TENANT_SUSPENDED_SUMMARY);
        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse reactivate(UUID publicId) {
        return reactivate(publicId, null);
    }

    @Transactional
    public TenantResponse reactivate(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_LIFECYCLE, TenancyConstants.OPERATION_TENANT_REACTIVATE);
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.reactivate();
        audit(caller, TenancyConstants.EVENT_TENANT_REACTIVATED, publicId,
                TenancyConstants.AUDIT_TENANT_REACTIVATED_SUMMARY);
        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateBranding(UUID publicId, UpdateBrandingRequest request) {
        return updateBranding(publicId, request, null);
    }

    @Transactional
    public TenantResponse updateBranding(UUID publicId, UpdateBrandingRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_BRANDING, TenancyConstants.OPERATION_TENANT_BRANDING);
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        tenant.updateBranding(request.logoUrl(), request.primaryColor());
        audit(caller, TenancyConstants.EVENT_TENANT_BRANDING_UPDATED, publicId,
                TenancyConstants.AUDIT_TENANT_BRANDING_UPDATED_SUMMARY);
        return TenantMapper.toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID publicId) {
        return get(publicId, null);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_READ, PlatformOperation.TENANT_READ.name());
        return TenantMapper.toResponse(tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return list(null);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list(CurrentUser caller) {
        require(caller, PlatformOperation.TENANT_READ, PlatformOperation.TENANT_READ.name());
        return tenantRepository.findAll().stream().map(TenantMapper::toResponse).toList();
    }

    private void require(CurrentUser caller, PlatformOperation operation, String action) {
        if (caller == null) {
            return;
        }
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (auditLogService != null) {
            auditLogService.recordFailure(caller, TenancyConstants.AGGREGATE_TENANT, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, action);
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    private void audit(CurrentUser caller, String action, UUID publicId, String summary) {
        if (auditLogService != null && caller != null && publicId != null) {
            auditLogService.record(caller, TenancyConstants.AGGREGATE_TENANT, publicId.toString(), action, summary);
        }
    }
}
