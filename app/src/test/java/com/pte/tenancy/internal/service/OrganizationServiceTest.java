package com.pte.tenancy.internal.service;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.exception.OrganizationNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.request.CreateOrganizationRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TenantRepository tenantRepository;


    private OrganizationService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, tenantRepository);
    }

    private Tenant tenantWithPublicId(UUID publicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setName("Acme School");
        return tenant;
    }

    private Organization activeOrganization(UUID orgPublicId, Tenant tenant) {
        Organization organization = new Organization();
        organization.setPublicId(orgPublicId);
        organization.setName("Downtown Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setTenant(tenant);
        return organization;
    }

    @Test
    void create_savesOrganizationUnderTenant() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        CreateOrganizationRequest request = new CreateOrganizationRequest("Downtown Branch", "123 Main St",
                FacilityType.BRANCH);
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.existsByTenant_PublicIdAndNameIgnoreCase(tenantPublicId, "Downtown Branch"))
                .thenReturn(false);
        // The service persists via organizationRepository.save(organization) directly
        // (NOT tenantRepository.save(tenant)) specifically so the exact `organization`
        // reference — not a merge()-created copy — receives the generated publicId.
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        OrganizationResponse response = service.create(tenantPublicId, request, caller);

        assertThat(response.publicId()).isNotNull();
        assertThat(response.name()).isEqualTo("Downtown Branch");
        assertThat(response.tenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(response.facilityType()).isEqualTo("BRANCH");
        assertThat(tenant.getOrganizations()).hasSize(1);
        assertThat(tenant.getOrganizations().get(0).getTenant()).isSameAs(tenant);
    }

    @Test
    void create_unknownTenant_throwsNotFoundWithoutSaving() {
        UUID tenantPublicId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(tenantPublicId,
                new CreateOrganizationRequest("Downtown Branch", null, FacilityType.BRANCH), caller))
                .isInstanceOf(TenantNotFoundException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void create_duplicateNameWithinTenant_throwsWithoutSaving() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.existsByTenant_PublicIdAndNameIgnoreCase(tenantPublicId, "Downtown Branch"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(tenantPublicId,
                new CreateOrganizationRequest("Downtown Branch", null, FacilityType.BRANCH), caller))
                .isInstanceOf(OrganizationNameAlreadyUsedException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void list_unknownTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        when(tenantRepository.existsByPublicId(tenantPublicId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(tenantPublicId, caller)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void list_returnsOrganizationsMappedWithTenantPublicId() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = activeOrganization(UUID.randomUUID(), tenant);
        when(tenantRepository.existsByPublicId(tenantPublicId)).thenReturn(true);
        when(organizationRepository.findByTenant_PublicIdOrderByCreatedAtAsc(tenantPublicId))
                .thenReturn(List.of(organization));

        List<OrganizationResponse> responses = service.list(tenantPublicId, caller);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).tenantPublicId()).isEqualTo(tenantPublicId);
    }

    @Test
    void get_organizationBelongsToMatchingTenant_returnsResponse() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, tenant);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        OrganizationResponse response = service.get(tenantPublicId, orgPublicId, caller);

        assertThat(response.publicId()).isEqualTo(orgPublicId);
        assertThat(response.tenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(response.name()).isEqualTo("Downtown Branch");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void get_organizationBelongsToDifferentTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID otherTenantPublicId = UUID.randomUUID();
        Tenant otherTenant = tenantWithPublicId(otherTenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, otherTenant);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.get(tenantPublicId, orgPublicId, caller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void suspend_activeOrganization_transitions() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, tenant);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        OrganizationResponse response = service.suspend(tenantPublicId, orgPublicId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_alreadySuspended_isIdempotentNoOp() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, tenant);
        organization.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        OrganizationResponse response = service.suspend(tenantPublicId, orgPublicId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    void reactivate_suspendedOrganization_transitions() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, tenant);
        organization.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        OrganizationResponse response = service.reactivate(tenantPublicId, orgPublicId, caller);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void reactivate_alreadyActive_isIdempotentNoOp() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, tenant);
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        OrganizationResponse response = service.reactivate(tenantPublicId, orgPublicId, caller);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void listForCaller_scopesToCallersOwnTenant() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = activeOrganization(UUID.randomUUID(), tenant);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(tenantRepository.existsByPublicId(tenantPublicId)).thenReturn(true);
        when(organizationRepository.findByTenant_PublicIdOrderByCreatedAtAsc(tenantPublicId))
                .thenReturn(List.of(organization));

        List<OrganizationResponse> responses = service.listForCaller(hostCaller);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).tenantPublicId()).isEqualTo(tenantPublicId);
    }

    @Test
    void getForCaller_organizationBelongsToDifferentTenant_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        Tenant otherTenant = tenantWithPublicId(otherTenantId);
        UUID orgPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(orgPublicId, otherTenant);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
        when(organizationRepository.findByPublicId(orgPublicId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.getForCaller(orgPublicId, hostCaller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }
}
