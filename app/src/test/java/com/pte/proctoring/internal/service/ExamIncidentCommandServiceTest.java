package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.proctoring.internal.mapper.ExamIncidentMapper;
import com.pte.proctoring.internal.policy.ExamIncidentAccessPolicy;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamIncidentCommandServiceTest {

    @Mock
    private ExamIncidentRepository incidentRepository;
    @Mock
    private IncidentHistoryLog historyLog;
    @Mock
    private IdentityService identityService;
    @Mock
    private SessionService sessionService;
    @Mock
    private AttemptService attemptService;

    private ExamIncidentCommandService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(hostId, tenantId, List.of("HOST_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new ExamIncidentCommandService(incidentRepository, historyLog, identityService, sessionService,
                attemptService, new ExamIncidentAccessPolicy(sessionService), new ExamIncidentMapper(), java.time.Clock.systemUTC());
        lenient().when(incidentRepository.save(any(ExamIncident.class))).thenAnswer(invocation -> {
            ExamIncident saved = invocation.getArgument(0);
            if (saved.getPublicId() == null) {
                saved.setPublicId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createIncident_savesAReportedIncidentAndWritesTheCreateHistoryStep() {
        IncidentView view = service.createIncident(sessionId,
                new CreateIncidentRequest(" Noise ", " Loud noise in the room ", "environment", "high", null, null),
                host);

        ArgumentCaptor<ExamIncident> saved = ArgumentCaptor.forClass(ExamIncident.class);
        verify(incidentRepository).save(saved.capture());
        ExamIncident incident = saved.getValue();
        assertThat(incident.getOrigin()).isEqualTo(IncidentOrigin.REPORTED);
        assertThat(incident.getType()).isEqualTo("ENVIRONMENT");
        assertThat(incident.getSeverity()).isEqualTo("HIGH");
        assertThat(incident.getStatus()).isEqualTo("NEW");
        assertThat(incident.getTitle()).isEqualTo("Noise");
        assertThat(incident.getTenantId()).isEqualTo(tenantId);
        assertThat(incident.getReporterPublicId()).isEqualTo(hostId);
        assertThat(view.category()).isEqualTo("ENVIRONMENT");
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
        verify(historyLog).record(eq(host), eq(incident.getPublicId()), any(IncidentAccessContext.class),
                eq("CREATE"), eq(null), eq("NEW"), eq(" Loud noise in the room "));
    }

    @Test
    void updateIncidentStatus_changesTheStatusAndWritesTheStatusHistoryStep() {
        ExamIncident incident = existing("NEW");
        when(incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndOriginAndDeletedFalse(
                incident.getPublicId(), sessionId, tenantId, IncidentOrigin.REPORTED))
                .thenReturn(Optional.of(incident));

        IncidentView view = service.updateIncidentStatus(sessionId, incident.getPublicId(),
                new UpdateIncidentStatusRequest("in_progress", "looking into it", null, null), host);

        assertThat(incident.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        verify(historyLog).record(eq(host), eq(incident.getPublicId()), any(IncidentAccessContext.class),
                eq("UPDATE_STATUS"), eq("NEW"), eq("IN_PROGRESS"), eq("looking into it"));
    }

    @Test
    void updateIncidentStatus_closedIncidentIsImmutableAndWritesNoHistory() {
        ExamIncident incident = existing("CLOSED");
        when(incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndOriginAndDeletedFalse(
                incident.getPublicId(), sessionId, tenantId, IncidentOrigin.REPORTED))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.updateIncidentStatus(sessionId, incident.getPublicId(),
                new UpdateIncidentStatusRequest("NEW", null, null, null), host))
                .isInstanceOf(ResponseStatusException.class);

        verify(historyLog, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private ExamIncident existing(String status) {
        ExamIncident incident = new ExamIncident();
        incident.setDetectedAt(java.time.Instant.now());
        incident.setPublicId(UUID.randomUUID());
        incident.setTenantId(tenantId);
        incident.setSessionPublicId(sessionId);
        incident.setOrigin(IncidentOrigin.REPORTED);
        incident.setType("ENVIRONMENT");
        incident.setTitle("t");
        incident.setSeverity("HIGH");
        incident.setStatus(status);
        return incident;
    }
}
