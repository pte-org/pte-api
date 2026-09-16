package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backs scoring's pull-based ingestion (Phase 08): scoring calls this
 * in-process instead of attempt pushing an {@code AnswerSubmitted} event —
 * attempt has no outbound dependency on scoring (dependency order:
 * attempt ──> scoring, not the reverse).
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

    private static SubmittedAnswerView toView(AttemptAnswer answer) {
        return new SubmittedAnswerView(answer.getPublicId(), answer.getAttempt().getPublicId(),
                answer.getPinnedItem().getPublicId(), answer.getAttempt().getSessionPublicId(),
                answer.getAttempt().getTenantId(), answer.getPinnedItem().getPinnedSnapshot().getScoreTemplatePublicId(),
                answer.getPinnedItem().getTaskType(), answer.getPayload(),
                answer.getPinnedItem().getCorrectAnswerText(), answer.getPinnedItem().getOptionsJson(),
                answer.isExpired());
    }
}
