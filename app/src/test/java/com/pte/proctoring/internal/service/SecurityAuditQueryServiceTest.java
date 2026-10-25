package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditQueryServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private ExamIncidentRepository incidentRepository;

    private SecurityAuditQueryService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SecurityAuditQueryService(new ProctorAccessPolicy(sessionService), incidentRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubAudit(List<ExamIncident> rows) {
        when(incidentRepository.findAuditFirstPage(eq(sessionId), eq(tenantId), any(Collection.class),
                any(Pageable.class))).thenReturn(rows);
    }

    @Test
    void hostMustOwnSession_andEntriesKeepTheirSourceAndOrder() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        UUID studentId = UUID.randomUUID();
        stubAudit(List.of(
                incident(IncidentOrigin.AUTO, studentId, "LOCKDOWN_FULLSCREEN_EXIT", "client-1",
                        Instant.parse("2026-09-28T04:00:01Z")),
                incident(IncidentOrigin.PROCTOR_FLAG, null, "TAB_SWITCH", null,
                        Instant.parse("2026-09-28T04:00:02Z"))));

        var result = service.listForSession(sessionId, 50, null, host);

        verify(sessionService).verifyHostAccess(sessionId, tenantId);
        assertThat(result.entries()).extracting(entry -> entry.source().name())
                .containsExactly("STUDENT_LOCKDOWN", "PROCTOR");
        assertThat(result.entries().get(0).clientEventId()).isEqualTo("client-1");
        assertThat(result.entries().get(0).studentPublicId()).isEqualTo(studentId);
        assertThat(result.entries().get(1).studentPublicId()).isNull();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void assignedProctorUsesAuthoritativeTenant_andReturnsCursorWhenMoreRowsExist() {
        UUID proctorId = UUID.randomUUID();
        CurrentUser proctor = new CurrentUser(proctorId, tenantId, List.of("PROCTOR"));
        when(sessionService.checkProctorAssignment(sessionId, proctorId))
                .thenReturn(new ProctorAssignmentCheckResponse(sessionId, tenantId));
        stubAudit(List.of(
                incident(IncidentOrigin.PROCTOR_FLAG, null, "TAB_SWITCH", null, Instant.parse("2026-09-28T04:00:01Z")),
                incident(IncidentOrigin.PROCTOR_FLAG, null, "TAB_SWITCH", null, Instant.parse("2026-09-28T04:00:02Z"))));

        var result = service.listForSession(sessionId, 1, null, proctor);

        verify(sessionService).checkProctorAssignment(sessionId, proctorId);
        verify(sessionService, never()).verifyHostAccess(any(), any());
        assertThat(result.entries()).hasSize(1);
        assertThat(result.nextCursor()).isNotBlank();
    }

    @SuppressWarnings("unchecked")
    @Test
    void nextPageReadsFromTheKeysetCursorOfTheLastEntry() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        ExamIncident first = incident(IncidentOrigin.AUTO, UUID.randomUUID(), "LOCKDOWN_FULLSCREEN_EXIT", "c1",
                Instant.parse("2026-09-28T04:00:01Z"));
        ExamIncident second = incident(IncidentOrigin.AUTO, UUID.randomUUID(), "LOCKDOWN_FULLSCREEN_EXIT", "c2",
                Instant.parse("2026-09-28T04:00:02Z"));
        stubAudit(List.of(first, second));
        String cursor = service.listForSession(sessionId, 1, null, host).nextCursor();
        when(incidentRepository.findAuditAfter(eq(sessionId), eq(tenantId), any(Collection.class),
                eq(first.getDetectedAt()), eq(first.getPublicId()), any(Pageable.class))).thenReturn(List.of(second));

        var page = service.listForSession(sessionId, 1, cursor, host);

        assertThat(page.entries()).extracting(entry -> entry.clientEventId()).containsExactly("c2");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void invalidLimitAndCursorAreRejected() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.listForSession(sessionId, 101, null, host))
                .hasMessage("SECURITY_AUDIT_LIMIT_INVALID");
        assertThatThrownBy(() -> service.listForSession(sessionId, 50, "not-a-cursor", host))
                .hasMessage("SECURITY_AUDIT_CURSOR_INVALID");
    }

    private ExamIncident incident(IncidentOrigin origin, UUID studentId, String type, String clientEventId,
                                  Instant detectedAt) {
        ExamIncident incident = new ExamIncident();
        incident.setPublicId(UUID.randomUUID());
        incident.setAttemptPublicId(UUID.randomUUID());
        incident.setStudentPublicId(studentId);
        incident.setOrigin(origin);
        incident.setType(type);
        incident.setSeverity("WARNING");
        incident.setDescription("detail");
        incident.setClientEventId(clientEventId);
        incident.setDetectedAt(detectedAt);
        return incident;
    }
}
