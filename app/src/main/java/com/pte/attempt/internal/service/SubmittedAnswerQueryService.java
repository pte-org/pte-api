package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.constant.AttemptConstants;
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
    private final ExamContentService contentService;

    public SubmittedAnswerQueryService(AttemptAnswerRepository attemptAnswerRepository,
            ExamContentService contentService) {
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.contentService = contentService;
    }

    @Transactional(readOnly = true)
    public List<SubmittedAnswerView> findForSession(UUID sessionPublicId, UUID tenantId) {
        return attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmittedAnswerView> findForAttempt(UUID attemptPublicId, UUID tenantId) {
        return attemptAnswerRepository.findByAttempt_PublicIdAndAttempt_TenantId(attemptPublicId, tenantId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<SubmittedAnswerView> findOne(UUID answerPublicId, UUID tenantId) {
        return attemptAnswerRepository.findByPublicIdAndAttempt_TenantId(answerPublicId, tenantId).map(this::toView);
    }

    private SubmittedAnswerView toView(AttemptAnswer answer) {
        ExamAttempt attempt = answer.getAttempt();
        SnapshotContentResponse.Item item = contentService.contentItems(attempt.getExamVersionPublicId())
                .get(answer.getItemPublicId());
        if (item == null) {
            throw new IllegalStateException(String.format(AttemptConstants.MISSING_ITEM_FOR_ANSWER,
                    answer.getItemPublicId()));
        }
        return new SubmittedAnswerView(answer.getPublicId(), attempt.getPublicId(), answer.getItemPublicId(),
                attempt.getSessionPublicId(), attempt.getTenantId(), attempt.getExamDefinitionPublicId(),
                item.taskType(), answer.getPayload(), item.correctAnswerText(), item.optionsJson(),
                answer.isExpired(), item.promptText(), item.referenceAnswerText(), item.imagePromptRef(),
                item.minWordCount(), item.maxWordCount(), item.section(), ExamContentService.effectiveRuntime(item),
                answer.getSelectedScoreSource(), answer.getSelectedScoreSourceAt(),
                answer.getSelectedScoreSourceByPublicId(), answer.getSelectedEvaluationPublicId());
    }
}
