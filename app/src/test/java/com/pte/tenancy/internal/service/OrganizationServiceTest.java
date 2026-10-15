package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.UpdateOrganizationProfileRequest;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.audit.AuditLogService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private VietnamLocationService vietnamLocationService;

    private OrganizationService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, tenantRepository, auditLogService);
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

    @Test
    void updateForCaller_updatesOrganizationAndTenantName_andRecordsAudit() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = activeOrganization(organizationPublicId, tenant);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        UpdateOrganizationProfileRequest request = new UpdateOrganizationProfileRequest(
                "  New School Name  ", "  1 Main Street  ", FacilityType.MAIN);
        when(tenantRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findWithLockByPublicId(organizationPublicId))
                .thenReturn(Optional.of(organization));
        when(tenantRepository.existsByName("New School Name")).thenReturn(false);

        OrganizationResponse response = service.updateForCaller(organizationPublicId, request, hostCaller);

        assertThat(tenant.getName()).isEqualTo("New School Name");
        assertThat(organization.getName()).isEqualTo("New School Name");
        assertThat(organization.getAddress()).isEqualTo("1 Main Street");
        assertThat(organization.getFacilityType()).isEqualTo(FacilityType.MAIN);
        assertThat(response.name()).isEqualTo("New School Name");
        assertThat(response.address()).isEqualTo("1 Main Street");
        verify(auditLogService).record(eq(hostCaller), eq(TenancyConstants.AGGREGATE_ORGANIZATION),
                eq(organizationPublicId.toString()), eq(TenancyConstants.EVENT_ORGANIZATION_UPDATED),
                eq(TenancyConstants.AUDIT_ORGANIZATION_UPDATED_SUMMARY));
    }

    @Test
    void updateForCaller_rejectsOrganizationFromAnotherTenant() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant callerTenant = tenantWithPublicId(callerTenantId);
        Organization organization = activeOrganization(organizationPublicId, tenantWithPublicId(otherTenantId));
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));
        when(tenantRepository.findWithLockByPublicId(callerTenantId)).thenReturn(Optional.of(callerTenant));
        when(organizationRepository.findWithLockByPublicId(organizationPublicId))
                .thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.updateForCaller(organizationPublicId,
                new UpdateOrganizationProfileRequest("New Name", null, FacilityType.MAIN), hostCaller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void updateForCaller_rejectsDuplicateTenantName() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = activeOrganization(organizationPublicId, tenant);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(tenantRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findWithLockByPublicId(organizationPublicId))
                .thenReturn(Optional.of(organization));
        when(tenantRepository.existsByName("Existing School")).thenReturn(true);

        assertThatThrownBy(() -> service.updateForCaller(organizationPublicId,
                new UpdateOrganizationProfileRequest("Existing School", null, FacilityType.MAIN), hostCaller))
                .isInstanceOf(TenantNameAlreadyUsedException.class);
    }

    @Test
    void updateForCaller_persistsResolvedProvinceAndWard() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        Organization organization = activeOrganization(organizationPublicId, tenant);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        service = new OrganizationService(
                organizationRepository, tenantRepository, auditLogService, vietnamLocationService);
        when(tenantRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findWithLockByPublicId(organizationPublicId))
                .thenReturn(Optional.of(organization));
        when(vietnamLocationService.resolve("79", "25747"))
                .thenReturn(new VietnamLocationService.ResolvedLocation(
                        "79", "Thành phố Hồ Chí Minh", "25747", "Phường Thủ Dầu Một"));

        OrganizationResponse response = service.updateForCaller(
                organizationPublicId,
                new UpdateOrganizationProfileRequest(
                        "Acme School", "1 Main Street", "79", "25747", FacilityType.MAIN),
                hostCaller);

        assertThat(organization.getProvinceCode()).isEqualTo("79");
        assertThat(organization.getProvinceName()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(organization.getWardCode()).isEqualTo("25747");
        assertThat(organization.getWardName()).isEqualTo("Phường Thủ Dầu Một");
        assertThat(response.provinceName()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(response.wardName()).isEqualTo("Phường Thủ Dầu Một");
    }
}
