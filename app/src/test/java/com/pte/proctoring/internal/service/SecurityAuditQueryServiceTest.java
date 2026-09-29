package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSecurityEventView;
import com.pte.proctoring.domain.ViolationEvent;
import com.pte.proctoring.internal.repository.ViolationEventRepository;
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
    private AttemptService attemptService;

    @Mock
    private ViolationEventRepository violationEventRepository;

    private SecurityAuditQueryService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SecurityAuditQueryService(sessionService, attemptService, violationEventRepository);
    }

    @Test
    void hostMustOwnSession_andMergedEntriesAreServerOrdered() {
        UUID hostId = UUID.randomUUID();
        CurrentUser host = new CurrentUser(hostId, tenantId, List.of("HOST_ADMIN"));
        UUID proctorEventId = UUID.randomUUID();
        UUID studentEventId = UUID.randomUUID();
        ViolationEvent proctorEvent = proctorEvent(proctorEventId, Instant.parse("2026-09-28T04:00:02Z"));
        when(violationEventRepository.findForSecurityAudit(eq(sessionId), eq(tenantId), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(proctorEvent));
        when(attemptService.getSecurityEventsForSession(eq(sessionId), eq(tenantId), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new AttemptSecurityEventView(studentEventId, UUID.randomUUID(), UUID.randomUUID(),
                        sessionId, "LOCKDOWN_FULLSCREEN_EXIT", "WARNING", "client-1", "detail",
                        null, Instant.parse("2026-09-28T04:00:01Z"))));

        var result = service.listForSession(sessionId, 50, null, host);

        verify(sessionService).verifyHostAccess(sessionId, tenantId);
        assertThat(result.entries()).extracting(entry -> entry.source().name())
                .containsExactly("STUDENT_LOCKDOWN", "PROCTOR");
        assertThat(result.entries().get(0).clientEventId()).isEqualTo("client-1");
    }

    @Test
    void assignedProctorUsesAuthoritativeTenant_andReturnsCursorWhenMoreRowsExist() {
        UUID proctorId = UUID.randomUUID();
        CurrentUser proctor = new CurrentUser(proctorId, UUID.randomUUID(), List.of("PROCTOR"));
        when(sessionService.checkProctorAssignment(sessionId, proctorId))
                .thenReturn(new ProctorAssignmentCheckResponse(sessionId, tenantId));
        when(violationEventRepository.findForSecurityAudit(eq(sessionId), eq(tenantId), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        proctorEvent(UUID.randomUUID(), Instant.parse("2026-09-28T04:00:01Z")),
                        proctorEvent(UUID.randomUUID(), Instant.parse("2026-09-28T04:00:02Z"))));

        var result = service.listForSession(sessionId, 1, null, proctor);

        verify(sessionService).checkProctorAssignment(sessionId, proctorId);
        verify(sessionService, never()).verifyHostAccess(any(), any());
        assertThat(result.entries()).hasSize(1);
        assertThat(result.nextCursor()).isNotBlank();
    }

    @Test
    void invalidLimitAndCursorAreRejected() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> service.listForSession(sessionId, 101, null, host))
                .hasMessage("SECURITY_AUDIT_LIMIT_INVALID");
        assertThatThrownBy(() -> service.listForSession(sessionId, 50, "not-a-cursor", host))
                .hasMessage("SECURITY_AUDIT_CURSOR_INVALID");
        verify(sessionService, never()).verifyHostAccess(any(), any());
    }

    private ViolationEvent proctorEvent(UUID publicId, Instant detectedAt) {
        ViolationEvent event = new ViolationEvent();
        event.setPublicId(publicId);
        event.setAttemptPublicId(UUID.randomUUID());
        event.setViolationType(com.pte.proctoring.domain.enums.ViolationType.TAB_SWITCH);
        event.setDetail("proctor detail");
        event.setDetectedAt(detectedAt);
        return event;
    }
}
