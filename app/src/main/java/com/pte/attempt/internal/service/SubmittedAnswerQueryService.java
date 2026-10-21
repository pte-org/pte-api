package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backs scoring's pull-based ingestion (Phase 08): scoring calls this
 * in-process instead of attempt pushing the answers to it — attempt has no
 * outbound dependency on scoring. The only push is the payload-free
 * {@code AttemptSubmittedEvent}, which tells scoring when to pull.
 */
@Service
public class SubmittedAnswerQueryService {

    private final AttemptAnswerRepository attemptAnswerRepository;

    public SubmittedAnswerQueryService(AttemptAnswerRepository attemptAnswerRepository) {
        this.attemptAnswerRepository = attemptAnswerRepository;
    }

    @Transactional(readOnly = true)
    public List<SubmittedAnswerView> findForSession(UUID sessionPublicId, UUID tenantId) {
        return attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId)
                .stream()
                .map(SubmittedAnswerQueryService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmittedAnswerView> findForAttempt(UUID attemptPublicId, UUID tenantId) {
        return attemptAnswerRepository.findByAttempt_PublicIdAndAttempt_TenantId(attemptPublicId, tenantId)
                .stream()
                .map(SubmittedAnswerQueryService::toView)
                .toList();
    }

    private static SubmittedAnswerView toView(AttemptAnswer answer) {
        PinnedItem item = answer.getPinnedItem();
        return new SubmittedAnswerView(answer.getPublicId(), answer.getAttempt().getPublicId(),
                item.getPublicId(), answer.getAttempt().getSessionPublicId(),
                answer.getAttempt().getTenantId(), item.getPinnedSnapshot().getScoreTemplatePublicId(),
                item.getTaskType(), answer.getPayload(),
                item.getCorrectAnswerText(), item.getOptionsJson(),
                answer.isExpired(), item.getPromptText(), item.getReferenceAnswerText(), item.getImagePromptRef(),
                item.getMinWordCount(), item.getMaxWordCount());
    }
}
