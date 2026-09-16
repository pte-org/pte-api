package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.exception.OrganizationNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.request.CreateOrganizationRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.mapper.OrganizationMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Organization (branch/facility) governance, scoped under a Tenant. Takes
 * {@code CurrentUser caller} on every method now (not strictly needed yet)
 * so Phase 5's audit trail isn't the first place introducing that convention
 * in `services/admin`.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;

    public OrganizationService(OrganizationRepository organizationRepository, TenantRepository tenantRepository) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public OrganizationResponse create(UUID tenantPublicId, CreateOrganizationRequest request, CurrentUser caller) {
        Tenant tenant = tenantRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        if (organizationRepository.existsByTenant_PublicIdAndNameIgnoreCase(tenantPublicId, request.name())) {
            throw new OrganizationNameAlreadyUsedException();
        }

        Organization organization = new Organization();
        organization.setName(request.name());
        organization.setAddress(request.address());
        organization.setFacilityType(request.facilityType());
        tenant.addOrganization(organization);
        // `tenant` is already managed (loaded above), so saving it would route
        // through entityManager.merge(), which for a transient element newly
        // added to a cascaded collection creates a SEPARATE COPY and persists
        // that instead of `organization` itself â€” the id would never land on
        // this reference. Persist the new child directly so `organization`
        // itself gets its generated publicId.
        Organization saved = organizationRepository.save(organization);        return OrganizationMapper.toResponse(saved, tenantPublicId);
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

    /** Host self-service: same as {@link #list}, scoped by the caller's own tenant instead of a path param. */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForCaller(CurrentUser caller) {
        return list(caller.tenantId(), caller);
    }

    /** Host self-service: same as {@link #get}, scoped by the caller's own tenant instead of a path param. */
    @Transactional(readOnly = true)
    public OrganizationResponse getForCaller(UUID organizationPublicId, CurrentUser caller) {
        return get(caller.tenantId(), organizationPublicId, caller);
    }

    @Transactional
    public OrganizationResponse suspend(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            return OrganizationMapper.toResponse(organization, tenantPublicId);
        }
        organization.suspend();        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    @Transactional
    public OrganizationResponse reactivate(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() == OrganizationStatus.ACTIVE) {
            return OrganizationMapper.toResponse(organization, tenantPublicId);
        }
        organization.reactivate();        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    /**
     * Loads an Organization strictly by (tenantPublicId, organizationPublicId)
     * pair â€” an org id that exists but belongs to a different tenant than the
     * path says is treated as not-found, not silently served, so the URL's
     * nesting is never just decorative.
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
