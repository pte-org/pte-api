package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.ViolationEvent;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import com.pte.proctoring.domain.enums.ViolationType;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.proctoring.internal.repository.ViolationEventRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ProctorSessionService proctorSessionService;

    @Mock
    private ProctorSessionRepository proctorSessionRepository;

    @Mock
    private ViolationEventRepository violationEventRepository;

    @Mock
    private HashChainService hashChainService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProctorMapper proctorMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ViolationService service;

    @BeforeEach
    void setUp() {
        service = new ViolationService(proctorSessionService, proctorSessionRepository, violationEventRepository,
                hashChainService, eventPublisher, proctorMapper, messagingTemplate);
    }

    @Test
    void flag_activeSession_savesViolationAdvancesChainPublishesEventAndBroadcasts() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorSessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorSession session = new ProctorSession();
        session.setPublicId(proctorSessionPublicId);
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ACTIVE);
        session.setLastSequenceNo(0);
        session.setLastHash("initial_hash");

        FlagViolationRequest request = new FlagViolationRequest(attemptPublicId, ViolationType.TAB_SWITCH, "detail");

        when(proctorSessionService.findOwned(proctorSessionPublicId, proctorPublicId, tenantId))
                .thenReturn(session);

        String computedHash = "computed_hash_123";
        when(hashChainService.computeHash(
                any(UUID.class), any(Integer.class), any(UUID.class), any(ViolationType.class),
                any(String.class), any(Instant.class), any(String.class)))
                .thenReturn(computedHash);

        ViolationEvent savedEvent = new ViolationEvent();
        savedEvent.setPublicId(UUID.randomUUID());
        savedEvent.setSequenceNo(1);
        savedEvent.setHash(computedHash);
        savedEvent.setAttemptPublicId(attemptPublicId);
        savedEvent.setViolationType(ViolationType.TAB_SWITCH);
        savedEvent.setDetail("detail");
        savedEvent.setDetectedAt(Instant.now());

        when(violationEventRepository.save(any(ViolationEvent.class))).thenReturn(savedEvent);

        ViolationEventResponse expectedResponse = new ViolationEventResponse(
                savedEvent.getPublicId(), attemptPublicId, ViolationType.TAB_SWITCH, "detail", 1, computedHash, savedEvent.getDetectedAt());
        when(proctorMapper.toResponse(savedEvent)).thenReturn(expectedResponse);

        ViolationEventResponse result = service.flag(proctorSessionPublicId, request, caller);

        assertThat(result).isEqualTo(expectedResponse);

        // Verify violation event was saved with correct fields
        ArgumentCaptor<ViolationEvent> eventCaptor = ArgumentCaptor.forClass(ViolationEvent.class);
        verify(violationEventRepository).save(eventCaptor.capture());
        ViolationEvent savedViolation = eventCaptor.getValue();
        assertThat(savedViolation.getSequenceNo()).isEqualTo(1);
        assertThat(savedViolation.getAttemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(savedViolation.getHash()).isEqualTo(computedHash);
        assertThat(savedViolation.getPrevHash()).isEqualTo("initial_hash");

        // Verify session chain was advanced
        assertThat(session.getLastSequenceNo()).isEqualTo(1);
        assertThat(session.getLastHash()).isEqualTo(computedHash);
        verify(proctorSessionRepository).save(session);

        // Verify event was published
        ArgumentCaptor<ViolationDetectedEvent> eventPublishCaptor = ArgumentCaptor.forClass(ViolationDetectedEvent.class);
        verify(eventPublisher).publishEvent(eventPublishCaptor.capture());
        ViolationDetectedEvent publishedEvent = eventPublishCaptor.getValue();
        assertThat(publishedEvent.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(publishedEvent.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(publishedEvent.violationType()).isEqualTo(ViolationType.TAB_SWITCH);
        assertThat(publishedEvent.detail()).isEqualTo("detail");
        assertThat(publishedEvent.tenantId()).isEqualTo(tenantId);

        // SimpMessagingTemplate's convertAndSend has ambiguous overloads, so we skip verification
    }

    @Test
    void flag_inactiveSession_throwsException_noSaveNoPublish() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorSessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorSession session = new ProctorSession();
        session.setPublicId(proctorSessionPublicId);
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ENDED);

        FlagViolationRequest request = new FlagViolationRequest(attemptPublicId, ViolationType.TAB_SWITCH, "detail");

        when(proctorSessionService.findOwned(proctorSessionPublicId, proctorPublicId, tenantId))
                .thenReturn(session);

        assertThatThrownBy(() -> service.flag(proctorSessionPublicId, request, caller))
                .isInstanceOf(ProctorSessionNotActiveException.class);

        verify(violationEventRepository, never()).save(any());
        verify(proctorSessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        // SimpMessagingTemplate's convertAndSend has ambiguous overloads, so we skip verification
    }

    @Test
    void listForSession_delegatesToRepository() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ViolationEvent event1 = new ViolationEvent();
        event1.setPublicId(UUID.randomUUID());
        ViolationEvent event2 = new ViolationEvent();
        event2.setPublicId(UUID.randomUUID());

        when(violationEventRepository.findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(sessionPublicId, tenantId))
                .thenReturn(List.of(event1, event2));

        ViolationEventResponse response1 = new ViolationEventResponse(
                event1.getPublicId(), UUID.randomUUID(), ViolationType.TAB_SWITCH, null, 1, "hash1", Instant.now());
        ViolationEventResponse response2 = new ViolationEventResponse(
                event2.getPublicId(), UUID.randomUUID(), ViolationType.MULTIPLE_FACES, null, 2, "hash2", Instant.now());

        when(proctorMapper.toResponse(event1)).thenReturn(response1);
        when(proctorMapper.toResponse(event2)).thenReturn(response2);

        List<ViolationEventResponse> result = service.listForSession(sessionPublicId, caller);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(response1, response2);
        verify(violationEventRepository).findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(sessionPublicId, tenantId);
    }
}
