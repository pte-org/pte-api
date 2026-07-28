package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.ViolationEvent;
import com.pte.proctor.domain.enums.LiveProctorEventType;
import com.pte.proctor.domain.enums.ViolationType;
import com.pte.proctor.domain.event.ViolationDetectedEvent;
import com.pte.proctor.dto.request.FlagViolationRequest;
import com.pte.proctor.dto.response.LiveProctorEventResponse;
import com.pte.proctor.dto.response.ViolationEventResponse;
import com.pte.proctor.mapper.ProctorMapper;
import com.pte.proctor.messaging.outbox.OutboxWriter;
import com.pte.proctor.repository.ProctorSessionRepository;
import com.pte.proctor.repository.ViolationEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationLiveEventTest {

    @Test
    void persistedViolationPublishesTypedEnvelopeWithStableEventId() {
        UUID proctorSessionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID proctorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ProctorSession session = activeSession(
                proctorSessionId, sessionId, proctorId, tenantId);
        ProctorSessionService sessionService = mock(ProctorSessionService.class);
        when(sessionService.findOwned(proctorSessionId, proctorId, tenantId))
                .thenReturn(session);
        ViolationEventRepository violationRepository =
                mock(ViolationEventRepository.class);
        when(violationRepository.save(any(ViolationEvent.class)))
                .thenAnswer(invocation -> {
                    ViolationEvent saved = invocation.getArgument(0);
                    saved.setPublicId(eventId);
                    return saved;
                });
        HashChainService hashChainService = mock(HashChainService.class);
        when(hashChainService.computeHash(any(), any(Integer.class), any(), any(), any(), any(), any()))
                .thenReturn("hash-1");
        ProctorMapper mapper = new ProctorMapper();
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ViolationService service = new ViolationService(
                sessionService,
                mock(ProctorSessionRepository.class),
                violationRepository,
                hashChainService,
                outboxWriter,
                mapper,
                messagingTemplate);
        FlagViolationRequest request = new FlagViolationRequest(
                UUID.randomUUID(),
                ViolationType.OTHER,
                "Repeated window switching");

        ViolationEventResponse response = service.flag(
                proctorSessionId,
                request,
                caller(proctorId, tenantId, "PROCTOR"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/proctor-sessions/" + sessionId),
                payload.capture());
        LiveProctorEventResponse<?> event =
                assertInstanceOf(LiveProctorEventResponse.class, payload.getValue());
        assertEquals(LiveProctorEventType.VIOLATION_DETECTED, event.eventType());
        assertEquals(eventId, event.eventId());
        assertEquals(sessionId, event.sessionPublicId());
        assertEquals(response, event.data());
        assertEquals(response.detectedAt(), event.occurredAt());
        verify(outboxWriter).write(
                eq("ViolationEvent"),
                eq(eventId.toString()),
                eq("ViolationDetected"),
                any(ViolationDetectedEvent.class),
                eq(tenantId));
    }

    @Test
    void proctorSnapshotRequiresActiveOwnedSessionButHostAuditRemainsTenantScoped() {
        UUID sessionId = UUID.randomUUID();
        UUID proctorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProctorSessionService sessionService = mock(ProctorSessionService.class);
        ViolationEventRepository violationRepository =
                mock(ViolationEventRepository.class);
        when(violationRepository.findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(
                sessionId, tenantId)).thenReturn(List.of());
        ViolationService service = new ViolationService(
                sessionService,
                mock(ProctorSessionRepository.class),
                violationRepository,
                mock(HashChainService.class),
                mock(OutboxWriter.class),
                new ProctorMapper(),
                mock(SimpMessagingTemplate.class));

        service.listForSession(sessionId, caller(proctorId, tenantId, "PROCTOR"));
        verify(sessionService).findActiveOwnedBySession(
                sessionId, proctorId, tenantId);

        service.listForSession(
                sessionId,
                caller(UUID.randomUUID(), tenantId, "HOST_ADMIN"));
        verify(sessionService, org.mockito.Mockito.times(1))
                .findActiveOwnedBySession(sessionId, proctorId, tenantId);
        verify(violationRepository, org.mockito.Mockito.times(2))
                .findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(sessionId, tenantId);
    }

    private ProctorSession activeSession(
            UUID publicId,
            UUID sessionId,
            UUID proctorId,
            UUID tenantId) {
        ProctorSession session = new ProctorSession();
        session.setPublicId(publicId);
        session.setSessionPublicId(sessionId);
        session.setProctorPublicId(proctorId);
        session.setTenantId(tenantId);
        return session;
    }

    private CurrentUser caller(UUID userId, UUID tenantId, String role) {
        return new CurrentUser(userId, tenantId, List.of(role));
    }
}
