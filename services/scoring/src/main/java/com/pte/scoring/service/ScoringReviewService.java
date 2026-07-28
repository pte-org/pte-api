package com.pte.scoring.service;

import com.pte.common.security.CurrentUser;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AnswerScoredEvent;
import com.pte.scoring.domain.exception.AnswerNotFoundException;
import com.pte.scoring.domain.exception.InvalidReviewQueryException;
import com.pte.scoring.domain.exception.ReviewNotPendingException;
import com.pte.scoring.dto.response.ScoringAnswerPageResponse;
import com.pte.scoring.dto.response.ScoringAnswerResponse;
import com.pte.scoring.mapper.ScoringAnswerMapper;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

/**
 * The human-review gate (phase-09): a host must explicitly approve an
 * AI-scored answer of a review-required task type (Write Essay — one of
 * Pearson's 7 sensitive types) before it counts as final. Tenant-scoped;
 * denial is 404, not 403 (no existence leak, same pattern as reporting).
 */
@Service
public class ScoringReviewService {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final OutboxWriter outboxWriter;
    private final AttemptCompletionService attemptCompletionService;

    public ScoringReviewService(ScoringAnswerRepository scoringAnswerRepository, OutboxWriter outboxWriter,
                                AttemptCompletionService attemptCompletionService) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.outboxWriter = outboxWriter;
        this.attemptCompletionService = attemptCompletionService;
    }

    @Transactional(readOnly = true)
    public ScoringAnswerPageResponse listPending(
            UUID sessionPublicId,
            String status,
            int page,
            int size,
            CurrentUser caller
    ) {
        if (!ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW.name().equals(status)
                || page < 0 || size < 1 || size > 100 || caller.tenantId() == null) {
            throw new InvalidReviewQueryException();
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "createdAt")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
        Page<ScoringAnswer> answers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndStatus(
                        sessionPublicId,
                        caller.tenantId(),
                        ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW,
                        pageable
                );

        return new ScoringAnswerPageResponse(
                answers.getContent().stream().map(ScoringAnswerMapper::toResponse).toList(),
                page,
                size,
                answers.getTotalElements(),
                answers.getTotalPages()
        );
    }

    @Transactional
    public ScoringAnswerResponse approve(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = findOwned(answerPublicId, caller);
        if (answer.getStatus() != ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW) {
            throw new ReviewNotPendingException();
        }

        int finalScore = answer.getRawScore();
        answer.markScored(finalScore);
        scoringAnswerRepository.save(answer);

        outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                ScoringConstants.EVENT_ANSWER_SCORED,
                new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(), answer.getTenantId(), finalScore),
                answer.getTenantId());
        attemptCompletionService.checkAndEmitIfComplete(answer.getAttemptPublicId(), answer.getSessionPublicId(), answer.getTenantId());

        return ScoringAnswerMapper.toResponse(answer);
    }

    private ScoringAnswer findOwned(UUID answerPublicId, CurrentUser caller) {
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(AnswerNotFoundException::new);
        if (!caller.isPlatformUser() && !answer.getTenantId().equals(caller.tenantId())) {
            throw new AnswerNotFoundException();
        }
        return answer;
    }
}
