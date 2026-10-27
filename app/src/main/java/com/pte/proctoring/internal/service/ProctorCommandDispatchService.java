package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.shared.security.CurrentUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Issues an attempt-affecting command against {@code attempt} — a direct in-process call to its public
 * force-submit API (no outbox: proctoring and attempt live in the same app). Broadcasts a confirmation to every
 * proctor watching the same exam session, so a multi-proctor session stays in sync.
 */
@Service
public class ProctorCommandDispatchService {

    private final ProctorWatchService watchService;
    private final AttemptService attemptService;
    private final SimpMessagingTemplate messagingTemplate;

    public ProctorCommandDispatchService(ProctorWatchService watchService, AttemptService attemptService,
                                         SimpMessagingTemplate messagingTemplate) {
        this.watchService = watchService;
        this.attemptService = attemptService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public boolean issueCommand(UUID sessionPublicId, IssueCommandRequest request, CurrentUser caller) {
        UUID tenantId = watchService.authorizeCommand(sessionPublicId, caller);
        if (!attemptService.attemptBelongsToSession(request.attemptPublicId(), sessionPublicId, tenantId)) {
            throw new TargetAttemptInvalidException();
        }
        boolean applied = attemptService.forceSubmitScoped(request.attemptPublicId(), sessionPublicId, tenantId);
        String topic = ProctorConstants.TOPIC_PREFIX + sessionPublicId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(topic, request);
                }
            });
        } else {
            messagingTemplate.convertAndSend(topic, request);
        }
        return applied;
    }
}
