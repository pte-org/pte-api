package com.pte.tenancy.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.dto.request.OrganizationPageRequest;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrganizationPageServiceTest {

    private final OrganizationRepository repository = mock(OrganizationRepository.class);
    private final OrganizationService service = new OrganizationService(repository);

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER"})
    void normalizesSearchAndPreservesDatabaseCountAndRequestedPage(String role) {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of(role));
        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID());
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        var pageable = PageRequest.of(1, 20);
        when(repository.findApprovedPage("school%", "school", pageable))
                .thenReturn(new PageImpl<>(List.of(organization), pageable, 45));

        var result = service.page(caller, new OrganizationPageRequest(1, 20, "  SCHOOL  "));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().publicId()).isEqualTo(organization.getPublicId());
        assertThat(result.meta().page()).isEqualTo(1);
        assertThat(result.meta().totalElements()).isEqualTo(45);
        assertThat(result.meta().totalPages()).isEqualTo(3);
        assertThat(result.meta().hasNext()).isTrue();
        verify(repository).findApprovedPage("school%", "school", pageable);
    }

    @Test
    void defaultRequestReadsOnlyTheFirstTwentyAndUsesAnEmptySearch() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findApprovedPage("%", "", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service.page(new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN")),
                new OrganizationPageRequest(null, null, null));

        assertThat(result.data()).isEmpty();
        assertThat(result.meta().size()).isEqualTo(20);
        assertThat(result.meta().totalElements()).isZero();
        verify(repository).findApprovedPage("%", "", pageable);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "STUDENT", "ACADEMIC_MANAGER"})
    void unauthorizedRolesCannotReadThePlatformDirectory(String role) {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of(role));

        assertThatThrownBy(() -> service.page(caller, new OrganizationPageRequest(0, 20, "")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
}
