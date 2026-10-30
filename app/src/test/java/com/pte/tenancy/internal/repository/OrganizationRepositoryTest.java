package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2: lookups that used to be split across TenantRepository and OrganizationRepository. */
@DataJpaTest
class OrganizationRepositoryTest {

    @Autowired
    private OrganizationRepository repository;

    @Test
    void aRejectedApplicationFreesItsCodeNameAndTaxCodeForReuse() {
        save("abc", "ABC School", "TAX-1", OrganizationStatus.REJECTED);
        save("xyz", "XYZ School", "TAX-2", OrganizationStatus.ACTIVE);
        save("pen", "Pending School", "TAX-3", OrganizationStatus.PENDING);

        assertThat(repository.existsByCodeAndStatusNot("abc", OrganizationStatus.REJECTED)).isFalse();
        assertThat(repository.existsByCodeAndStatusNot("xyz", OrganizationStatus.REJECTED)).isTrue();
        assertThat(repository.existsByCodeAndStatusNot("pen", OrganizationStatus.REJECTED)).isTrue();
        assertThat(repository.existsByNameAndStatusNot("ABC School", OrganizationStatus.REJECTED)).isFalse();
        assertThat(repository.existsByTaxCodeAndStatusNot("TAX-2", OrganizationStatus.REJECTED)).isTrue();
    }

    @Test
    void activeLookupIgnoresPendingRejectedSuspendedAndDeletedOrganizations() {
        Organization active = save("act", "Active", "T-A", OrganizationStatus.ACTIVE);
        Organization suspended = save("sus", "Suspended", "T-S", OrganizationStatus.SUSPENDED);
        Organization pending = save("pen", "Pending", "T-P", OrganizationStatus.PENDING);
        Organization deleted = save("del", "Deleted", "T-D", OrganizationStatus.ACTIVE);
        deleted.setDeleted(true);
        repository.flush();

        Set<UUID> ids = Set.of(active.getPublicId(), suspended.getPublicId(), pending.getPublicId(),
                deleted.getPublicId());

        assertThat(repository.findByPublicIdInAndStatusAndDeletedFalse(ids, OrganizationStatus.ACTIVE))
                .extracting(Organization::getPublicId).containsExactly(active.getPublicId());
    }

    @Test
    void platformSeatLimitsListOnlyLiveOrganizations() {
        Organization live = save("liv", "Live", "T-L", OrganizationStatus.ACTIVE);
        live.setStudentLimit(250);
        Organization gone = save("gon", "Gone", "T-G", OrganizationStatus.ACTIVE);
        gone.setDeleted(true);
        repository.flush();

        List<?> limits = repository.findAllSeatLimits();

        assertThat(limits).hasSize(1);
    }

    private Organization save(String code, String name, String taxCode, OrganizationStatus status) {
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(name);
        organization.setOrganizationType("UNIVERSITY");
        organization.setTaxCode(taxCode);
        organization.setPackageName("starter");
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(status);
        return repository.saveAndFlush(organization);
    }
}
