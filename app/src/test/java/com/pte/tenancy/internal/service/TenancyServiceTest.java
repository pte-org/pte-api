package com.pte.tenancy.internal.service;

import com.pte.tenancy.StudentCountProvider;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.internal.exception.InvalidStudentCountException;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
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
    private TenantRepository tenantRepository;

    @Mock
    private TenantLifecycleService tenantLifecycleService;

    @Mock
    private QuotaTransactionService quotaTransactionService;

    @Mock
    private StudentCountProvider studentCountProvider;

    private TenancyService tenancyService;

    @BeforeEach
    void setUp() {
        tenancyService = new TenancyService(organizationRepository, tenantRepository, tenantLifecycleService,
                quotaTransactionService, studentCountProvider);
    }

    @Test
    void getStudentQuota_returnsCurrentCountAndTenantLimit() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 1000);
        when(tenantRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant));
        when(studentCountProvider.countStudents(tenantId)).thenReturn(487L);

        StudentQuota quota = tenancyService.getStudentQuota(tenantId);

        assertThat(quota.current()).isEqualTo(487L);
        assertThat(quota.limit()).isEqualTo(1000L);
        assertThat(quota.remaining()).isEqualTo(513L);
        assertThat(quota.canAdd(500L)).isTrue();
    }

    @Test
    void getStudentLimit_returnsTenantLimit() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 1000)));

        assertThat(tenancyService.getStudentLimit(tenantId)).isEqualTo(1000);
    }

    @Test
    void assertCanAddStudents_locksTenantBeforeCountingAndAllowsWithinLimit() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 100)));
        when(studentCountProvider.countStudents(tenantId)).thenReturn(40L);

        tenancyService.assertCanAddStudents(tenantId, 60L);

        InOrder order = inOrder(tenantRepository, studentCountProvider);
        order.verify(tenantRepository).findWithLockByPublicId(tenantId);
        order.verify(studentCountProvider).countStudents(tenantId);
    }

    @Test
    void assertCanAddStudents_exceedingLimitIncludesAllThreeCountsAndDoesNotWrite() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.of(tenant(tenantId, 100)));
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

        verify(tenantRepository, never()).findWithLockByPublicId(any());
        verify(studentCountProvider, never()).countStudents(any());
    }

    @Test
    void assertCanAddStudents_unknownTenantFailsWithoutCounting() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findWithLockByPublicId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenancyService.assertCanAddStudents(tenantId, 1L))
                .isInstanceOf(TenantNotFoundException.class);

        verify(studentCountProvider, never()).countStudents(any());
    }

    private Tenant tenant(UUID publicId, int studentLimit) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setStudentLimit(studentLimit);
        return tenant;
    }
}
