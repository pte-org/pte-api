package com.pte.admin.repository;

import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.FacilityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} only — proves the derived query's {@code DeletedFalse}
 * keyword actually excludes archived rows at the JPA/SQL level (first real
 * usage of {@code BaseEntity.deleted} in this repo; a Mockito-mocked
 * repository test in {@code ProgramServiceTest} can only prove the service
 * forwards whatever the repository returns, not that the query itself is
 * correct).
 */
@DataJpaTest
class ProgramRepositoryTest {

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Organization persistOrganization() {
        Tenant tenant = new Tenant();
        tenant.setName("Acme School " + UUID.randomUUID());
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        tenant = tenantRepository.save(tenant);

        Organization organization = new Organization();
        organization.setName("Downtown Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        return organizationRepository.save(organization);
    }

    private Program programUnder(Organization organization, String name, boolean deleted) {
        Program program = new Program();
        program.setOrganization(organization);
        program.setName(name);
        program.setDeleted(deleted);
        return programRepository.save(program);
    }

    @Test
    void findByOrganization_excludesArchivedRows() {
        Organization organization = persistOrganization();
        Program active = programUnder(organization, "Khối 12", false);
        programUnder(organization, "Khối 11 (archived)", true);

        List<Program> results = programRepository
                .findByOrganization_PublicIdAndDeletedFalseOrderByCreatedAtAsc(organization.getPublicId());

        assertThat(results).extracting(Program::getPublicId).containsExactly(active.getPublicId());
    }

    @Test
    void existsByOrganizationAndName_ignoresArchivedRows() {
        Organization organization = persistOrganization();
        programUnder(organization, "Khối 12", true);

        boolean exists = programRepository.existsByOrganization_PublicIdAndNameIgnoreCaseAndDeletedFalse(
                organization.getPublicId(), "khối 12");

        assertThat(exists).isFalse();
    }

    @Test
    void findByPublicId_stillReturnsArchivedRow() {
        Organization organization = persistOrganization();
        Program archived = programUnder(organization, "Khối 12", true);

        assertThat(programRepository.findByPublicId(archived.getPublicId())).isPresent();
    }
}
