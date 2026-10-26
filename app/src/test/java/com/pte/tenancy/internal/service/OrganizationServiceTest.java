package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.UpdateOrganizationProfileRequest;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.repository.OrganizationRepository;
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
    private AuditLogService auditLogService;

    @Mock
    private VietnamLocationService vietnamLocationService;

    private OrganizationService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, auditLogService, vietnamLocationService);
    }

    private Organization activeOrganization(UUID publicId) {
        Organization organization = new Organization();
        organization.setPublicId(publicId);
        organization.setName("Acme School");
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    @Test
    void list_platformCaller_returnsAllApprovedOrganizations() {
        UUID organizationPublicId = UUID.randomUUID();
        when(organizationRepository.findAllApproved()).thenReturn(List.of(activeOrganization(organizationPublicId)));

        List<OrganizationResponse> responses = service.list(caller);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).publicId()).isEqualTo(organizationPublicId);
    }

    @Test
    void list_hostCaller_scopesToCallersOwnOrganization() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(tenantPublicId);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(organizationRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(organization));

        List<OrganizationResponse> responses = service.list(hostCaller);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).publicId()).isEqualTo(tenantPublicId);
    }

    @Test
    void list_hostCaller_pendingOrganizationIsNotListed() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(tenantPublicId);
        organization.setStatus(OrganizationStatus.PENDING);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(organizationRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(organization));

        assertThat(service.list(hostCaller)).isEmpty();
    }

    @Test
    void get_unknownOrganization_throwsNotFound() {
        UUID organizationPublicId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(organizationPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(organizationPublicId, caller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void get_platformCaller_returnsResponse() {
        UUID organizationPublicId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(organizationPublicId))
                .thenReturn(Optional.of(activeOrganization(organizationPublicId)));

        OrganizationResponse response = service.get(organizationPublicId, caller);

        assertThat(response.publicId()).isEqualTo(organizationPublicId);
        assertThat(response.name()).isEqualTo("Acme School");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void get_hostCallerOwnOrganization_returnsResponse() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(organizationRepository.findByPublicId(tenantPublicId))
                .thenReturn(Optional.of(activeOrganization(tenantPublicId)));

        OrganizationResponse response = service.get(tenantPublicId, hostCaller);

        assertThat(response.publicId()).isEqualTo(tenantPublicId);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void get_organizationDifferentFromCallersTenant_throwsNotFound() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.get(otherOrganizationId, hostCaller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void updateProfile_updatesOrganization_andRecordsAudit() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(tenantPublicId);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        UpdateOrganizationProfileRequest request = new UpdateOrganizationProfileRequest(
                "  New School Name  ", "  1 Main Street  ", FacilityType.MAIN);
        when(organizationRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(organization));
        when(organizationRepository.existsByNameAndStatusNot("New School Name", OrganizationStatus.REJECTED))
                .thenReturn(false);
        when(vietnamLocationService.resolve(null, null))
                .thenReturn(VietnamLocationService.ResolvedLocation.empty());

        OrganizationResponse response = service.updateProfile(tenantPublicId, request, hostCaller);

        assertThat(organization.getName()).isEqualTo("New School Name");
        assertThat(organization.getAddress()).isEqualTo("1 Main Street");
        assertThat(organization.getFacilityType()).isEqualTo(FacilityType.MAIN);
        assertThat(response.name()).isEqualTo("New School Name");
        assertThat(response.address()).isEqualTo("1 Main Street");
        verify(auditLogService).record(eq(hostCaller), eq(TenancyConstants.AGGREGATE_ORGANIZATION),
                eq(tenantPublicId.toString()), eq(TenancyConstants.EVENT_ORGANIZATION_UPDATED),
                eq(TenancyConstants.AUDIT_ORGANIZATION_UPDATED_SUMMARY));
    }

    @Test
    void updateProfile_rejectsOrganizationDifferentFromCallersTenant() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.updateProfile(otherOrganizationId,
                new UpdateOrganizationProfileRequest("New Name", null, FacilityType.MAIN), hostCaller))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void updateProfile_rejectsDuplicateOrganizationName() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(tenantPublicId);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(organizationRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(organization));
        when(organizationRepository.existsByNameAndStatusNot("Existing School", OrganizationStatus.REJECTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(tenantPublicId,
                new UpdateOrganizationProfileRequest("Existing School", null, FacilityType.MAIN), hostCaller))
                .isInstanceOf(TenantNameAlreadyUsedException.class);
    }

    @Test
    void updateProfile_persistsResolvedProvinceAndWard() {
        UUID tenantPublicId = UUID.randomUUID();
        Organization organization = activeOrganization(tenantPublicId);
        CurrentUser hostCaller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        when(organizationRepository.findWithLockByPublicId(tenantPublicId)).thenReturn(Optional.of(organization));
        when(vietnamLocationService.resolve("79", "25747"))
                .thenReturn(new VietnamLocationService.ResolvedLocation(
                        "79", "Thành phố Hồ Chí Minh", "25747", "Phường Thủ Dầu Một"));

        OrganizationResponse response = service.updateProfile(
                tenantPublicId,
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
