package com.pte.shared.audit;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void list_noAggregateTypeFilter_returnsAllRowsForCallerTenantOnly() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        AuditLog row = rowOf(tenantPublicId);

        when(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantPublicId)).thenReturn(List.of(row));

        List<AuditLogResponse> result = service.list(caller, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).publicId()).isEqualTo(row.getPublicId());
    }

    @Test
    void list_withAggregateTypeFilter_forwardsBothTenantAndFilter() {
        UUID tenantPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));
        AuditLog row = rowOf(tenantPublicId);

        when(auditLogRepository.findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(
                tenantPublicId, EnrollmentConstants.AGGREGATE_PROGRAM)).thenReturn(List.of(row));

        List<AuditLogResponse> result = service.list(caller, EnrollmentConstants.AGGREGATE_PROGRAM);

        assertThat(result).hasSize(1);
        verify(auditLogRepository, org.mockito.Mockito.never()).findByTenantIdOrderByCreatedAtDesc(any());
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
