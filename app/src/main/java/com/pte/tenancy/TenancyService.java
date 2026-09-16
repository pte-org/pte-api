package com.pte.tenancy;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Public in-process API for tenant data needed by other modules. */
@Service
public class TenancyService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;

    public TenancyService(OrganizationRepository organizationRepository, TenantRepository tenantRepository) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
    }

    /** Returns the tenant's display type without exposing tenancy repositories. */
    public Optional<String> findOrganizationType(UUID tenantId) {
        return tenantRepository.findOrganizationTypeByPublicId(tenantId);
    }

    /** Resolves an organization only when it belongs to the supplied tenant. */
    public Organization findOrganizationOwned(UUID organizationPublicId, UUID tenantId) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (organization.getTenant() == null
                || !organization.getTenant().getPublicId().equals(tenantId)) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }
}
