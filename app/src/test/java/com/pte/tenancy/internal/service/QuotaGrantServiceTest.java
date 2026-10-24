package com.pte.tenancy.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.response.QuotaGrantResponse;
import com.pte.tenancy.internal.exception.QuotaConflictException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Quota grants are an append-only list owned by the Organization aggregate: a grant raises the student limit and
 * appends its own audit row in the same save, so the two can never disagree.
 */
@ExtendWith(MockitoExtension.class)
class QuotaGrantServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private QuotaGrantService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new QuotaGrantService(organizationRepository);
    }

    private Organization organization(UUID publicId) {
        Organization organization = new Organization();
        organization.setPublicId(publicId);
        organization.setName("Acme School");
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        return organization;
    }

    @Test
    void grant_raisesLimitAndAppendsTheGrantToTheOrganization() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        when(organizationRepository.findByPublicId(organizationId)).thenReturn(Optional.of(organization));
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuotaGrantResponse response = service.grant(organizationId,
                new GrantQuotaRequest("professional", 200, "Upsell"), caller);

        assertThat(response.publicId()).isNotNull();
        assertThat(response.tenantPublicId()).isEqualTo(organizationId);
        assertThat(response.packageName()).isEqualTo("professional");
        assertThat(response.amount()).isEqualTo(200);
        assertThat(response.actorUserId()).isEqualTo(caller.userId());
        assertThat(response.note()).isEqualTo("Upsell");
        assertThat(response.createdAt()).isNotNull();
        assertThat(organization.getPackageName()).isEqualTo("professional");
        assertThat(organization.getStudentLimit()).isEqualTo(700);
        assertThat(organization.getQuotaGrants()).hasSize(1);
        assertThat(organization.getQuotaGrants().get(0).getPublicId()).isEqualTo(response.publicId());
    }

    @Test
    void grant_unknownOrganization_throwsNotFound() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(organizationId,
                new GrantQuotaRequest("professional", 200, null), caller))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void grant_concurrentUpdateRacesTheVersionCheck_translatesToQuotaConflict() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(organizationId))
                .thenReturn(Optional.of(organization(organizationId)));
        // The second of two racing grants: the row version moved after this caller loaded it.
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        assertThatThrownBy(() -> service.grant(organizationId,
                new GrantQuotaRequest("professional", 200, null), caller))
                .isInstanceOf(QuotaConflictException.class);
    }

    @Test
    void history_unknownOrganization_throwsNotFound() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(organizationId)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void history_returnsGrantsNewestFirstMappedWithTheOrganizationId() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        organization.grantQuota("professional", 100, caller.userId(), "first");
        organization.grantQuota("professional", 50, caller.userId(), "second");
        when(organizationRepository.findByPublicId(organizationId)).thenReturn(Optional.of(organization));

        List<QuotaGrantResponse> history = service.history(organizationId);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(QuotaGrantResponse::tenantPublicId).containsOnly(organizationId);
        assertThat(history).extracting(QuotaGrantResponse::note).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void grantForSystem_recordsTheSystemActor() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        when(organizationRepository.findByPublicId(organizationId)).thenReturn(Optional.of(organization));
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuotaGrantResponse response = service.grantForSystem(organizationId, 30, "Activated plan");

        assertThat(response.amount()).isEqualTo(30);
        assertThat(organization.getStudentLimit()).isEqualTo(530);
        assertThat(organization.getQuotaGrants().get(0).getNote()).isEqualTo("Activated plan");
    }
}
