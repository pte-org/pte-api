package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.event.ProctorCommandPublished;
import com.pte.proctor.domain.enums.LiveProctorEventType;
import com.pte.proctor.domain.enums.ProctorCommandType;
import com.pte.proctor.dto.request.IssueCommandRequest;
import com.pte.proctor.dto.response.LiveProctorEventResponse;
import com.pte.proctor.messaging.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProctorCommandLiveEventTest {

    @Test
    void acceptedCommandPublishesTypedLiveEnvelopeWithoutChangingRequestData() {
        UUID proctorSessionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID proctorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProctorSession session = new ProctorSession();
        session.setPublicId(proctorSessionId);
        session.setSessionPublicId(sessionId);
        session.setProctorPublicId(proctorId);
        session.setTenantId(tenantId);
        ProctorSessionService sessionService = mock(ProctorSessionService.class);
        when(sessionService.findOwned(proctorSessionId, proctorId, tenantId))
                .thenReturn(session);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        ProctorCommandService service = new ProctorCommandService(
                sessionService,
                outboxWriter,
                messagingTemplate);
        IssueCommandRequest request = new IssueCommandRequest(
                UUID.randomUUID(),
                ProctorCommandType.EXTEND_TIME,
                300);

        service.issueCommand(
                proctorSessionId,
                request,
                new CurrentUser(proctorId, tenantId, List.of("PROCTOR")));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/proctor-sessions/" + sessionId),
                payload.capture());
        LiveProctorEventResponse<?> event =
                assertInstanceOf(LiveProctorEventResponse.class, payload.getValue());
        assertEquals(LiveProctorEventType.COMMAND_ACCEPTED, event.eventType());
        assertEquals(sessionId, event.sessionPublicId());
        assertEquals(request, event.data());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        verify(outboxWriter).write(
                eq("ProctorCommand"),
                eq(request.attemptPublicId().toString()),
                eq("ProctorCommand"),
                any(ProctorCommandPublished.class),
                eq(tenantId));
    }
}
