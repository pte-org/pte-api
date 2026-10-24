package com.pte.tenancy.internal.service;

import com.pte.tenancy.StudentCountProvider;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.dto.response.StudentQuotaResponse;
import com.pte.tenancy.internal.exception.InvalidStudentCountException;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantSuspendedException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenancyServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;


    @Mock
    private OrganizationLifecycleService organizationLifecycleService;

    @Mock
    private QuotaGrantService quotaGrantService;

    @Mock
    private StudentCountProvider studentCountProvider;

    private TenancyService tenancyService;

    @BeforeEach
    void setUp() {
        tenancyService = new TenancyService(organizationRepository, organizationLifecycleService,
                quotaGrantService, studentCountProvider);
    }

    @Test
    void getStudentQuota_returnsCurrentCountAndTenantLimit() {
        UUID tenantId = UUID.randomUUID();
        Organization tenant = tenant(tenantId, 1000);
        when(organizationRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant));
        when(studentCountProvider.countStudents(tenantId)).thenReturn(487L);

        StudentQuota quota = tenancyService.getStudentQuota(tenantId);

        assertThat(quota.current()).isEqualTo(487L);
        assertThat(quota.limit()).isEqualTo(1000L);
        assertThat(quota.remaining()).isEqualTo(513L);
        assertThat(StudentQuotaResponse.from(quota, 500L).remaining()).isEqualTo(13L);
        assertThat(quota.canAdd(500L)).isTrue();
    }

    @Test
    void getStudentLimit_returnsTenantLimit() {
        UUID tenantId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 1000)));

        assertThat(tenancyService.getStudentLimit(tenantId)).isEqualTo(1000);
    }

    @Test
    void assertCanAddStudents_locksTenantBeforeCountingAndAllowsWithinLimit() {
        UUID tenantId = UUID.randomUUID();
        when(organizationRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 100)));
        when(studentCountProvider.countStudents(tenantId)).thenReturn(40L);

        tenancyService.assertCanAddStudents(tenantId, 60L);

        InOrder order = inOrder(organizationRepository, studentCountProvider);
        order.verify(organizationRepository).findWithLockByPublicId(tenantId);
        order.verify(studentCountProvider).countStudents(tenantId);
    }

    @Test
    void assertCanAddStudents_exceedingLimitIncludesAllThreeCountsAndDoesNotWrite() {
        UUID tenantId = UUID.randomUUID();
        when(organizationRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 100)));
        when(studentCountProvider.countStudents(tenantId)).thenReturn(95L);

        assertThatThrownBy(() -> tenancyService.assertCanAddStudents(tenantId, 10L))
                .isInstanceOfSatisfying(StudentLimitExceededException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCurrent()).isEqualTo(95L);
                    assertThat(ex.getLimit()).isEqualTo(100L);
                    assertThat(ex.getAdding()).isEqualTo(10L);
                    assertThat(ex.getMessage()).isEqualTo("STUDENT_LIMIT_EXCEEDED: current=95, limit=100, adding=10");
                });
    }

    @Test
    void assertCanAddStudents_invalidCountFailsBeforeLock() {
        assertThatThrownBy(() -> tenancyService.assertCanAddStudents(UUID.randomUUID(), 0L))
                .isInstanceOf(InvalidStudentCountException.class);

        verify(organizationRepository, never()).findWithLockByPublicId(any());
        verify(studentCountProvider, never()).countStudents(any());
    }

    @Test
    void assertCanAddStudents_unknownTenantFailsWithoutCounting() {
        UUID tenantId = UUID.randomUUID();
        when(organizationRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenancyService.assertCanAddStudents(tenantId, 1L))
                .isInstanceOf(TenantNotFoundException.class);

        verify(studentCountProvider, never()).countStudents(any());
    }

    @Test
    void assertTenantActive_rejectsSuspendedTenant() {
        UUID tenantId = UUID.randomUUID();
        Organization tenant = tenant(tenantId, 100);
        tenant.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> tenancyService.assertTenantActive(tenantId))
                .isInstanceOfSatisfying(TenantSuspendedException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(403));
    }

    @Test
    void assertTenantActive_allowsActiveTenant() {
        UUID tenantId = UUID.randomUUID();
        when(organizationRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 100)));

        tenancyService.assertTenantActive(tenantId);
    }

    @Test
    void findLoginOrganization_allowsSuspendedTenantForAuthentication() {
        UUID tenantId = UUID.randomUUID();
        Organization tenant = tenant(tenantId, 100);
        tenant.setCode("suspended-school");
        tenant.setName("Suspended School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setStatus(OrganizationStatus.SUSPENDED);
        when(organizationRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant));

        assertThat(tenancyService.findLoginOrganization(tenantId)).isPresent()
                .get().extracting("tenantId").isEqualTo(tenantId);
    }

    private Organization tenant(UUID publicId, int studentLimit) {
        Organization tenant = new Organization();
        tenant.setPublicId(publicId);
        tenant.setStatus(OrganizationStatus.ACTIVE);
        tenant.setStudentLimit(studentLimit);
        return tenant;
    }
}
