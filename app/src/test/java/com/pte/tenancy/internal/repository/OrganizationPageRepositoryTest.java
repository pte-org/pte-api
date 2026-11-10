package com.pte.tenancy.internal.repository;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real JPA limits, counts and eligibility before DTO mapping. */
@DataJpaTest
class OrganizationPageRepositoryTest {

    @Autowired
    private OrganizationRepository repository;

    @Test
    void pagesAreBoundedCountEveryMatchAndKeepDuplicateNamesInStableOrder() {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            ids.add(save("org-" + index, "Same name", "TAX-" + index,
                    OrganizationStatus.ACTIVE).getId());
        }

        Page<Organization> first = page("", 0);
        Page<Organization> second = page("", 1);
        Page<Organization> last = page("", 2);

        assertThat(first.getContent()).extracting(Organization::getId).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(second.getContent()).extracting(Organization::getId).containsExactlyElementsOf(ids.subList(20, 40));
        assertThat(last.getContent()).extracting(Organization::getId).containsExactlyElementsOf(ids.subList(40, 45));
        assertThat(first.getTotalElements()).isEqualTo(45);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(last.isLast()).isTrue();
        assertThat(page("", 3)).isEmpty();
    }

    @Test
    void allAndFilteredPagesExcludePendingRejectedAndDeletedButIncludeSuspended() {
        Organization active = save("eligible-active", "A active", "EL-A", OrganizationStatus.ACTIVE);
        Organization suspended = save("eligible-suspended", "B suspended", "EL-S", OrganizationStatus.SUSPENDED);
        save("eligible-pending", "Pending", "EL-P", OrganizationStatus.PENDING);
        save("eligible-rejected", "Rejected", "EL-R", OrganizationStatus.REJECTED);
        Organization deleted = save("eligible-deleted", "Deleted", "EL-D", OrganizationStatus.ACTIVE);
        deleted.setDeleted(true);
        repository.flush();

        for (String term : List.of("", "eligible")) {
            Page<Organization> result = page(term, 0);
            assertThat(result.getContent()).extracting(Organization::getId)
                    .containsExactly(active.getId(), suspended.getId());
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @Test
    void filtersByCodeAndTaxPrefixOrExactCaseInsensitiveEmailBeforeCounting() {
        Organization byCode = save("school-code", "B code", "C-1", OrganizationStatus.ACTIVE);
        Organization byTax = save("other", "A tax", "SCHOOL-TAX", OrganizationStatus.ACTIVE);
        Organization byEmail = save("mail", "C mail", "M-1", OrganizationStatus.ACTIVE);
        byEmail.setContactEmail("Office@School.Example");
        repository.flush();

        assertThat(page("school", 0).getContent()).extracting(Organization::getId)
                .containsExactly(byTax.getId(), byCode.getId());
        assertThat(page("office@school.example", 0).getContent()).extracting(Organization::getId)
                .containsExactly(byEmail.getId());
        assertThat(page("office@school", 0).getTotalElements()).isZero();
    }

    private Page<Organization> page(String term, int index) {
        return repository.findApprovedPage(term + "%", term, PageRequest.of(index, 20));
    }

    private Organization save(String code, String name, String taxCode, OrganizationStatus status) {
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(name);
        organization.setOrganizationType("UNIVERSITY");
        organization.setTaxCode(taxCode);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(status);
        return repository.saveAndFlush(organization);
    }
}
