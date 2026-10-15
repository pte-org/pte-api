package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The derived count behind {@code TenancyService.countOrganizations}: soft-delete filter and
 * tenant isolation. The schema allows one organization per tenant (uk_organizations_tenant),
 * so a tenant's count is 0 or 1.
 */
@DataJpaTest
class OrganizationCountQueryTest {

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private TenantRepository tenants;

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setCode("t-" + UUID.randomUUID());
        tenant.setName("Tenant " + UUID.randomUUID());
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        return tenants.save(tenant);
    }

    private void organization(Tenant tenant, boolean deleted) {
        Organization organization = new Organization();
        organization.setName("Branch " + UUID.randomUUID());
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        organization.setDeleted(deleted);
        organizations.saveAndFlush(organization);
    }

    @Test
    void countsOnlyNonDeletedOrganizationsOfTheGivenTenant() {
        Tenant live = tenant();
        Tenant archived = tenant();
        Tenant without = tenant();
        organization(live, false);
        organization(archived, true); // soft-deleted

        assertThat(organizations.countByTenant_PublicIdAndDeletedFalse(live.getPublicId())).isEqualTo(1);
        assertThat(organizations.countByTenant_PublicIdAndDeletedFalse(archived.getPublicId())).isZero();
        assertThat(organizations.countByTenant_PublicIdAndDeletedFalse(without.getPublicId())).isZero();
        assertThat(organizations.countByTenant_PublicIdAndDeletedFalse(UUID.randomUUID())).isZero();
    }
}
