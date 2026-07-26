package com.pte.scoring.messaging.consumer;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AnswerScoredEvent;
import com.pte.scoring.messaging.job.AiScoringJob;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ScoringAnswerRepository;
import com.pte.scoring.service.AttemptCompletionService;
import com.pte.scoring.vendor.AiScoreResult;
import com.pte.scoring.vendor.EssayScoringClient;
import com.pte.scoring.vendor.SpeechScoringClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Consumes {@link AiScoringJob} from the RabbitMQ work queue. Dispatches by
 * task type to the vendor client (stub this phase — see phase-09 Design
 * Constraints). {@code WRITE_ESSAY} (one of Pearson's 7 human-review-required
 * types) lands in {@code AI_SCORED_PENDING_REVIEW}; {@code READ_ALOUD} (not
 * one of the 7) goes straight to {@code SCORED} + emits {@code AnswerScored}.
 *
 * <p>Any exception here propagates to the container's retry advice
 * ({@code RabbitMqConfig}) — bounded retry with backoff, then dead-lettered;
 * {@link #onDeadLettered} marks the row {@code SCORING_FAILED} so a host sees
 * it, instead of it silently vanishing into the DLQ.
 */
@Component
public class AiScoringWorker {

    private static final Set<String> REVIEW_REQUIRED_TASK_TYPES = Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY);

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final SpeechScoringClient speechScoringClient;
    private final EssayScoringClient essayScoringClient;
    private final OutboxWriter outboxWriter;
    private final AttemptCompletionService attemptCompletionService;

    public AiScoringWorker(ScoringAnswerRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient, OutboxWriter outboxWriter,
                           AttemptCompletionService attemptCompletionService) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.speechScoringClient = speechScoringClient;
        this.essayScoringClient = essayScoringClient;
        this.outboxWriter = outboxWriter;
        this.attemptCompletionService = attemptCompletionService;
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void onAiScoringJob(AiScoringJob job) {
        Optional<ScoringAnswer> maybeAnswer = scoringAnswerRepository.findByAnswerPublicId(job.answerPublicId());
        if (maybeAnswer.isEmpty()) {
            return;
        }
        ScoringAnswer answer = maybeAnswer.get();
        if (answer.getStatus() == ScoringAnswerStatus.SCORED
                || answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED
                || answer.getStatus() == ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW) {
            return; // Already terminal, or already awaiting host review — redelivery no-op, do not re-call the vendor.
        }

        AiScoreResult result = callVendor(job);

        if (REVIEW_REQUIRED_TASK_TYPES.contains(job.taskType())) {
            answer.setStatus(ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW);
            answer.setRawScore(result.rawScore());
            scoringAnswerRepository.save(answer);
            // No AnswerScored yet — waits for ScoringReviewService's host approval.
        } else {
            answer.markScored(result.rawScore());
            scoringAnswerRepository.save(answer);
            outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                    ScoringConstants.EVENT_ANSWER_SCORED,
                    new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(),
                            answer.getTenantId(), result.rawScore()),
                    answer.getTenantId());
            attemptCompletionService.checkAndEmitIfComplete(answer.getAttemptPublicId(), job.sessionPublicId(), answer.getTenantId());
        }
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_DLQ, containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void onDeadLettered(AiScoringJob job) {
        scoringAnswerRepository.findByAnswerPublicId(job.answerPublicId()).ifPresent(answer -> {
            answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);
            scoringAnswerRepository.save(answer);
        });
    }

    private AiScoreResult callVendor(AiScoringJob job) {
        if (ScoringConstants.TASK_TYPE_READ_ALOUD.equals(job.taskType())) {
            return speechScoringClient.score(job.payload(), job.referenceText());
        }
        return essayScoringClient.score(job.payload(), job.referenceText());
    }
}
