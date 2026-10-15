package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Issues an attempt-affecting command against {@code attempt} — a direct
 * in-process call to its public force-submit API (no outbox: proctoring and
 * attempt live in the same app now). Broadcasts a confirmation to every
 * proctor watching the same exam session, so a multi-proctor session stays
 * in sync.
 */
@Service
public class ProctorCommandDispatchService {

    private final ProctorSessionService proctorSessionService;
    private final ProctorSessionRepository proctorSessionRepository;
    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ProctorCommandDispatchService(ProctorSessionService proctorSessionService,
                                         ProctorSessionRepository proctorSessionRepository,
                                         SessionService sessionService,
                                         AttemptService attemptService,
                                         SimpMessagingTemplate messagingTemplate) {
        this.proctorSessionService = proctorSessionService;
        this.proctorSessionRepository = proctorSessionRepository;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.messagingTemplate = messagingTemplate;
    }

    public ProctorCommandDispatchService(ProctorSessionService proctorSessionService, AttemptService attemptService,
                                         SimpMessagingTemplate messagingTemplate) {
        this(proctorSessionService, null, null, attemptService, messagingTemplate);
    }

    @Transactional
    public boolean issueCommand(UUID proctorSessionPublicId, IssueCommandRequest request, CurrentUser caller) {
        ProctorSession session = proctorSessionRepository != null
                ? proctorSessionRepository.findWithLockByPublicIdAndProctorPublicIdAndTenantId(
                        proctorSessionPublicId, caller.userId(), caller.tenantId())
                        .orElseGet(() -> proctorSessionService.findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId()))
                : proctorSessionService.findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId());

        if (!session.isActive()) {
            throw new ProctorSessionNotActiveException();
        }

        if (sessionService != null) {
            sessionService.lockOpenWithinWindowForProctorOperation(session.getSessionPublicId(), session.getTenantId());
            sessionService.checkProctorAssignment(session.getSessionPublicId(), caller.userId());
        }

        boolean applied;
        if (sessionService != null && attemptService != null) {
            if (!attemptService.attemptBelongsToSession(request.attemptPublicId(), session.getSessionPublicId(), session.getTenantId())) {
                throw new TargetAttemptInvalidException();
            }
            applied = attemptService.forceSubmitScoped(request.attemptPublicId(), session.getSessionPublicId(), session.getTenantId());
        } else {
            attemptService.forceSubmit(request.attemptPublicId(), session.getTenantId());
            applied = true;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(), request);
                }
            });
        } else {
            messagingTemplate.convertAndSend(ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(), request);
        }

        return applied;
    }
}
