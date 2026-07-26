package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.constant.ProctorConstants;
import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.enums.ProctorCommandType;
import com.pte.proctor.domain.event.ProctorCommandPublished;
import com.pte.proctor.domain.exception.ExtraSecondsRequiredException;
import com.pte.proctor.domain.exception.ProctorSessionNotActiveException;
import com.pte.proctor.dto.request.IssueCommandRequest;
import com.pte.proctor.messaging.outbox.OutboxWriter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Issues an attempt-affecting command against exam-delivery — ALWAYS async,
 * via the {@code ProctorCommand} outbox event (ADR-002: proctor never calls
 * exam-delivery directly). Broadcasts a confirmation to every proctor watching
 * the same exam session, so a multi-proctor session stays in sync.
 */
@Service
public class ProctorCommandService {

    private final ProctorSessionService proctorSessionService;
    private final OutboxWriter outboxWriter;
    private final SimpMessagingTemplate messagingTemplate;

    public ProctorCommandService(ProctorSessionService proctorSessionService, OutboxWriter outboxWriter,
                                 SimpMessagingTemplate messagingTemplate) {
        this.proctorSessionService = proctorSessionService;
        this.outboxWriter = outboxWriter;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void issueCommand(UUID proctorSessionPublicId, IssueCommandRequest request, CurrentUser caller) {
        ProctorSession session = proctorSessionService.findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId());
        if (!session.isActive()) {
            throw new ProctorSessionNotActiveException();
        }
        if (request.commandType() == ProctorCommandType.EXTEND_TIME
                && (request.extraSeconds() == null || request.extraSeconds() <= 0)) {
            throw new ExtraSecondsRequiredException();
        }

        outboxWriter.write(ProctorConstants.AGGREGATE_PROCTOR_COMMAND, request.attemptPublicId().toString(),
                ProctorConstants.EVENT_PROCTOR_COMMAND,
                new ProctorCommandPublished(request.attemptPublicId(), session.getSessionPublicId(),
                        request.commandType(), request.extraSeconds(), session.getTenantId()),
                session.getTenantId());

        messagingTemplate.convertAndSend(ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(), request);
    }
}
