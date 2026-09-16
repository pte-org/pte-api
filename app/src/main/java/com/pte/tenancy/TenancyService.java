package com.pte.tenancy;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.tenancy.internal.service.TenantLifecycleService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Public in-process API for tenant data needed by other modules. */
@Service
public class TenancyService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final TenantLifecycleService tenantLifecycleService;

    public TenancyService(OrganizationRepository organizationRepository, TenantRepository tenantRepository,
            TenantLifecycleService tenantLifecycleService) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
        this.tenantLifecycleService = tenantLifecycleService;
    }

    /** {@code billing.TenantApplicationService.submit()} — is this code still free to reserve? */
    public boolean existsByCode(String code) {
        return tenantRepository.existsByCode(code);
    }

    /** {@code billing.TenantApplicationService.approve()} — creates the tenant an approved application promised. */
    public Tenant createTenant(String name, String organizationType, String code, int studentLimit) {
        return tenantLifecycleService.createFromApplication(name, organizationType, code, studentLimit);
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
