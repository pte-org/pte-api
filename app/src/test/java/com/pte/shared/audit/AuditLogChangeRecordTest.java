package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Phase 1: AuditLog becomes the queryable history source (attendance history, source-selection
 * audits, incident history), so it must keep actor role, request id and before/after state.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogChangeRecordTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogChangeWriter writer;

    @InjectMocks
    private AuditLogService service;

    @Test
    void recordChangeKeepsRoleRequestIdAndBeforeAfterState() {
        UUID actorId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(actorId, organizationId, List.of("HOST_ADMIN"));

        writer.record(caller, new AuditChange("ATTENDANCE", "student-1", "ATTENDANCE_MARKED", "Marked PRESENT",
                requestId, "{\"status\":\"UNMARKED\"}", "{\"status\":\"PRESENT\"}"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getTenantId()).isEqualTo(organizationId);
        assertThat(saved.getActorRole()).isEqualTo("HOST_ADMIN");
        assertThat(saved.getRequestPublicId()).isEqualTo(requestId);
        assertThat(saved.getBeforeState()).isEqualTo("{\"status\":\"UNMARKED\"}");
        assertThat(saved.getAfterState()).isEqualTo("{\"status\":\"PRESENT\"}");
        assertThat(saved.getAggregateType()).isEqualTo("ATTENDANCE");
        assertThat(saved.getAggregateId()).isEqualTo("student-1");
    }

    @Test
    void existingRecordOverloadStillWorksWithoutTheNewFields() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

        service.record(caller, "CLASS", "c-1", "CLASS_CREATED", "Created");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getBeforeState()).isNull();
        assertThat(captor.getValue().getRequestPublicId()).isNull();
    }
}
