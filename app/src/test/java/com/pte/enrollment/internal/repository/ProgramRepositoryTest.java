package com.pte.enrollment.internal.repository;

import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
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

    private Organization persistOrganization() {
        Organization organization = new Organization();
        organization.setCode("acme-" + UUID.randomUUID());
        organization.setName("Acme School " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
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
