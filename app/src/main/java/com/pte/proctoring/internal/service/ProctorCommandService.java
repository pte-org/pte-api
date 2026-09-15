package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.shared.security.CurrentUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Issues an attempt-affecting command against {@code attempt} — a direct
 * in-process call to its public force-submit API (no outbox: proctoring and
 * attempt live in the same app now). Broadcasts a confirmation to every
 * proctor watching the same exam session, so a multi-proctor session stays
 * in sync.
 */
@Service
public class ProctorCommandService {

    private final ProctorSessionService proctorSessionService;
    private final AttemptService attemptService;
    private final SimpMessagingTemplate messagingTemplate;

    public ProctorCommandService(ProctorSessionService proctorSessionService, AttemptService attemptService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.proctorSessionService = proctorSessionService;
        this.attemptService = attemptService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void issueCommand(UUID proctorSessionPublicId, IssueCommandRequest request, CurrentUser caller) {
        ProctorSession session = proctorSessionService.findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId());
        if (!session.isActive()) {
            throw new ProctorSessionNotActiveException();
        }

        // Only command type today (EXTEND_TIME removed, see ProctorCommandType) —
        // silent no-op on a stale/missing/inactive attempt, matching attempt's own semantics.
        attemptService.forceSubmit(request.attemptPublicId(), session.getTenantId());

        messagingTemplate.convertAndSend(ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(), request);
    }
}
