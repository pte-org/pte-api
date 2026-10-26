package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The existence check behind {@code TenancyService.countOrganizations}: an organization is the tenant, so a
 * tenant's count is 1 when its organization row exists and 0 otherwise, and one tenant never sees another's.
 */
@DataJpaTest
class OrganizationCountQueryTest {

    @Autowired
    private OrganizationRepository organizations;

    private Organization organization() {
        Organization organization = new Organization();
        organization.setCode("t-" + UUID.randomUUID());
        organization.setName("Tenant " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organizations.saveAndFlush(organization);
    }

    @Test
    void existsOnlyForTheGivenTenantsOwnOrganization() {
        Organization live = organization();
        Organization other = organization();

        assertThat(organizations.existsByPublicId(live.getPublicId())).isTrue();
        assertThat(organizations.existsByPublicId(other.getPublicId())).isTrue();
        assertThat(organizations.existsByPublicId(UUID.randomUUID())).isFalse();
    }
}
