package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.domain.enums.ViolationType;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ProctorWatchService watchService;

    @Mock
    private ExamIncidentRepository incidentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AttemptService attemptService;

    @Mock
    private ProctorAccessPolicy proctorAccessPolicy;

    private final ProctorMapper mapper = new ProctorMapper();
    private ViolationService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID proctorPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptPublicId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

    @BeforeEach
    void setUp() {
        service = new ViolationService(watchService, incidentRepository, eventPublisher, mapper, messagingTemplate,
                attemptService, proctorAccessPolicy, java.time.Clock.systemUTC());
    }

    @Test
    void flag_attemptOfTheSession_recordsAProctorFlagPublishesEventAndBroadcasts() {
        FlagViolationRequest request = new FlagViolationRequest(attemptPublicId, ViolationType.TAB_SWITCH, "detail");
        when(watchService.authorizeCommand(sessionPublicId, caller)).thenReturn(tenantId);
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(true);
        when(incidentRepository.save(any(ExamIncident.class))).thenAnswer(invocation -> {
            ExamIncident saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ViolationEventResponse result = service.flag(sessionPublicId, request, caller);

        ArgumentCaptor<ExamIncident> saved = ArgumentCaptor.forClass(ExamIncident.class);
        verify(incidentRepository).save(saved.capture());
        ExamIncident incident = saved.getValue();
        assertThat(incident.getOrigin()).isEqualTo(IncidentOrigin.PROCTOR_FLAG);
        assertThat(incident.getType()).isEqualTo("TAB_SWITCH");
        assertThat(incident.getDescription()).isEqualTo("detail");
        assertThat(incident.getTenantId()).isEqualTo(tenantId);
        assertThat(incident.getSessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(incident.getAttemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(incident.getReporterPublicId()).isEqualTo(proctorPublicId);
        assertThat(result.violationType()).isEqualTo(ViolationType.TAB_SWITCH);
        assertThat(result.proctorPublicId()).isEqualTo(proctorPublicId);

        ArgumentCaptor<ViolationDetectedEvent> published = ArgumentCaptor.forClass(ViolationDetectedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(published.getValue().sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(published.getValue().violationType()).isEqualTo(ViolationType.TAB_SWITCH);
        assertThat(published.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void flag_attemptOfAnotherSession_isRejectedWithoutRecordingOrPublishing() {
        FlagViolationRequest request = new FlagViolationRequest(attemptPublicId, ViolationType.TAB_SWITCH, "detail");
        when(watchService.authorizeCommand(sessionPublicId, caller)).thenReturn(tenantId);
        when(attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.flag(sessionPublicId, request, caller))
                .isInstanceOf(TargetAttemptInvalidException.class);

        verify(incidentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void listForSession_returnsTheSessionsProctorFlagsOldestFirst() {
        ExamIncident first = flag(ViolationType.TAB_SWITCH, Instant.parse("2026-09-28T04:00:00Z"));
        ExamIncident second = flag(ViolationType.MULTIPLE_FACES, Instant.parse("2026-09-28T04:00:05Z"));
        when(proctorAccessPolicy.authorizeReadAssigned(sessionPublicId, caller)).thenReturn(tenantId);
        when(incidentRepository.findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
                sessionPublicId, tenantId, IncidentOrigin.PROCTOR_FLAG)).thenReturn(List.of(first, second));

        List<ViolationEventResponse> result = service.listForSession(sessionPublicId, caller);

        assertThat(result).extracting(ViolationEventResponse::violationType)
                .containsExactly(ViolationType.TAB_SWITCH, ViolationType.MULTIPLE_FACES);
    }

    private ExamIncident flag(ViolationType type, Instant detectedAt) {
        ExamIncident incident = new ExamIncident();
        incident.setDetectedAt(java.time.Instant.now());
        incident.setPublicId(UUID.randomUUID());
        incident.setOrigin(IncidentOrigin.PROCTOR_FLAG);
        incident.setType(type.name());
        incident.setAttemptPublicId(attemptPublicId);
        incident.setReporterPublicId(proctorPublicId);
        incident.setDetectedAt(detectedAt);
        return incident;
    }
}
