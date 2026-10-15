package com.pte.shared.audit;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository);
    }

    @Test
    void record_savesRowWithActorAndTenantFromCaller() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(actorUserId, tenantPublicId, List.of("HOST_ADMIN"));

        service.record(caller, EnrollmentConstants.AGGREGATE_CLASS, classPublicId.toString(),
                EnrollmentConstants.EVENT_CLASS_CREATED, "Created Class \"12A1\"");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.getTenantId()).isEqualTo(tenantPublicId);
        assertThat(saved.getAggregateType()).isEqualTo(EnrollmentConstants.AGGREGATE_CLASS);
        assertThat(saved.getAggregateId()).isEqualTo(classPublicId.toString());
        assertThat(saved.getAction()).isEqualTo(EnrollmentConstants.EVENT_CLASS_CREATED);
        assertThat(saved.getSummary()).isEqualTo("Created Class \"12A1\"");
    }

    @Test
    void record_platformCaller_allowsNullTenantForPlatformAudit() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        service.record(caller, "SCORE_TEMPLATE", UUID.randomUUID().toString(), "ACTIVATED", "Activated version 2");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isNull();
    }

    @Test
    void record_capturesHumanReadableResourceIdentitySeparatelyFromInternalAggregateId() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

        service.record(caller, "ExamSession", UUID.randomUUID().toString(), "Mock exam", "HCM-261014-AB12",
                "ExamPublished", "Published Mock exam");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getResourceName()).isEqualTo("Mock exam");
        assertThat(saved.getResourceCode()).isEqualTo("HCM-261014-AB12");
    }

    @Test
    void list_noAggregateTypeFilter_returnsAllRowsForCallerTenantOnly() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        AuditLog row = rowOf(tenantPublicId);

        when(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantPublicId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        var result = service.list(caller, null, 0, 20);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).publicId()).isEqualTo(row.getPublicId());
        assertThat(result.meta().totalElements()).isEqualTo(1);
    }

    @Test
    void list_withAggregateTypeFilter_forwardsBothTenantAndFilter() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        AuditLog row = rowOf(tenantPublicId);

        when(auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(
                tenantPublicId, EnrollmentConstants.AGGREGATE_PROGRAM, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        var result = service.list(caller, EnrollmentConstants.AGGREGATE_PROGRAM, 0, 20);

        assertThat(result.data()).hasSize(1);
        verify(auditLogRepository, org.mockito.Mockito.never())
                .findByTenantIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void list_withDateRange_forwardsUtcDayBoundaries() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        AuditLog row = rowOf(tenantPublicId);
        var pageable = PageRequest.of(0, 20);
        Instant createdFrom = LocalDate.of(2026, 10, 13).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant createdTo = LocalDate.of(2026, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant();

        when(auditLogRepository.findForTenant(tenantPublicId, null, null, createdFrom, createdTo, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        var result = service.list(caller, null, null,
                LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), 0, 20);

        assertThat(result.data()).hasSize(1);
        verify(auditLogRepository).findForTenant(tenantPublicId, null, null, createdFrom, createdTo, pageable);
    }

    @Test
    void listPlatform_returnsTenantlessRowsOnly() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER"));
        AuditLog row = rowOf(null);

        when(auditLogRepository.findByTenantIdIsNullOrderByCreatedAtDesc(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        var result = service.listPlatform(caller, null, 0, 20);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).publicId()).isEqualTo(row.getPublicId());
        verify(auditLogRepository).findByTenantIdIsNullOrderByCreatedAtDesc(PageRequest.of(0, 20));
    }

    @Test
    void listPlatform_withSearch_forwardsPlatformScopeAndSearch() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        AuditLog row = rowOf(null);

        when(auditLogRepository.findForPlatform("Plan", "updated", null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        var result = service.listPlatform(caller, "Plan", " updated ", 0, 20);

        assertThat(result.data()).hasSize(1);
        verify(auditLogRepository).findForPlatform("Plan", "updated", null, null, PageRequest.of(0, 20));
    }

    private AuditLog rowOf(UUID tenantPublicId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPublicId(UUID.randomUUID());
        auditLog.setTenantId(tenantPublicId);
        auditLog.setActorUserId(UUID.randomUUID());
        auditLog.setAggregateType(EnrollmentConstants.AGGREGATE_CLASS);
        auditLog.setAggregateId(UUID.randomUUID().toString());
        auditLog.setAction(EnrollmentConstants.EVENT_CLASS_CREATED);
        auditLog.setSummary("Created Class \"12A1\"");
        return auditLog;
    }
}
