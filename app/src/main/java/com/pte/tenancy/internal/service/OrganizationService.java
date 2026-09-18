package com.pte.tenancy.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.mapper.OrganizationMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read and lifecycle operations for the single Organization owned by a Tenant.
 * Organizations are provisioned automatically when a Tenant is created.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;

    public OrganizationService(OrganizationRepository organizationRepository, TenantRepository tenantRepository) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(UUID tenantPublicId, CurrentUser caller) {
        if (!tenantRepository.existsByPublicId(tenantPublicId)) {
            throw new TenantNotFoundException();
        }
        return organizationRepository.findByTenant_PublicIdOrderByCreatedAtAsc(tenantPublicId).stream()
                .map(organization -> OrganizationMapper.toResponse(organization, tenantPublicId))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    /** Host self-service view, scoped by the caller's own tenant. */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForCaller(CurrentUser caller) {
        return list(caller.tenantId(), caller);
    }

    /** Host self-service view, scoped by the caller's own tenant. */
    @Transactional(readOnly = true)
    public OrganizationResponse getForCaller(UUID organizationPublicId, CurrentUser caller) {
        return get(caller.tenantId(), organizationPublicId, caller);
    }

    @Transactional
    public OrganizationResponse suspend(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() != OrganizationStatus.SUSPENDED) {
            organization.suspend();
        }
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    @Transactional
    public OrganizationResponse reactivate(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            organization.reactivate();
        }
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    /**
     * Loads an Organization strictly by tenant and organization pair. A
     * cross-tenant id is treated as not found so the URL never leaks scope.
     */
    private Organization loadUnderTenant(UUID tenantPublicId, UUID organizationPublicId) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (!organization.getTenant().getPublicId().equals(tenantPublicId)) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }
}
