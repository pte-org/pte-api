package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.service.AiScoringResultPersistenceService;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consumes {@link AiScoringJob} from the RabbitMQ work queue. Dispatches by
 * the catalog's task modality to the vendor client (stub by default). Every
 * AI-scored task type — {@code WRITE_ESSAY} included — goes straight to
 * {@code SCORED}; there is no host-approval hold. A host's own independent
 * score, if any, is recorded separately via {@code
 * ScoringReviewService.submitTeacherScore} and never gates this.
 *
 * <p>Any exception here propagates to the container's retry advice ({@code
 * RabbitMqConfig}) — bounded retry with backoff, then dead-lettered; {@link
 * #onDeadLettered} marks the row {@code SCORING_FAILED} so a host sees it,
 * instead of it silently vanishing into the DLQ.
 */
@Component
public class AiScoringWorker {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final SpeechScoringClient speechScoringClient;
    private final EssayScoringClient essayScoringClient;
    private final AiScoringResultPersistenceService resultPersistenceService;

    public AiScoringWorker(ScoringAnswerRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient,
                           AiScoringResultPersistenceService resultPersistenceService) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.speechScoringClient = speechScoringClient;
        this.essayScoringClient = essayScoringClient;
        this.resultPersistenceService = resultPersistenceService;
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_QUEUE, containerFactory = "scoringRabbitListenerContainerFactory")
    public void onAiScoringJob(AiScoringJob job) {
        Optional<ScoringAnswer> maybeAnswer = scoringAnswerRepository.findByAnswerPublicId(job.answerPublicId());
        if (maybeAnswer.isEmpty()) {
            return;
        }
        ScoringAnswer answer = maybeAnswer.get();
        if (answer.getStatus() == ScoringAnswerStatus.SCORED
                || answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED) {
            return; // Already terminal — redelivery no-op, do not re-call the vendor.
        }
        if (!matchesJob(answer, job)) {
            return;
        }
        if (resultPersistenceService.isPublicationLocked(job.tenantId(), job.sessionPublicId())) {
            return;
        }

        AiScoreResult result = callVendor(job);
        resultPersistenceService.persistAiScore(job.answerPublicId(), job.attemptPublicId(), job.tenantId(),
                job.sessionPublicId(), result);
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_DLQ, containerFactory = "scoringRabbitListenerContainerFactory")
    public void onDeadLettered(AiScoringJob job) {
        resultPersistenceService.markScoringFailed(job.answerPublicId(), job.attemptPublicId(), job.tenantId(),
                job.sessionPublicId());
    }

    private boolean matchesJob(ScoringAnswer answer, AiScoringJob job) {
        return answer.getTenantId().equals(job.tenantId())
                && answer.getSessionPublicId().equals(job.sessionPublicId())
                && answer.getAttemptPublicId().equals(job.attemptPublicId());
    }

    /** Switches on {@code job.scoringMethod()} — resolved once at dispatch time from the pinned ScoreTemplate (spec FR-07), not re-derived from task type here. */
    private AiScoreResult callVendor(AiScoringJob job) {
        return switch (job.scoringMethod()) {
            case "AI_SPEECH" -> speechScoringClient.score(job.payload(), job.referenceText(), job.tenantId());
            case "AI_TEXT" -> essayScoringClient.score(job.payload(), job.referenceText());
            default -> throw new IllegalArgumentException(
                    String.format(ScoringConstants.UNSUPPORTED_AI_TASK_TYPE, job.taskType()));
        };
    }
}
