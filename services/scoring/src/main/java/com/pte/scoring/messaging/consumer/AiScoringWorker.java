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

/**
 * Consumes {@link AiScoringJob} from the RabbitMQ work queue. Dispatches by
 * task type to the vendor client (stub this phase — see phase-09 Design
 * Constraints). Every AI-scored task type — {@code WRITE_ESSAY} included as
 * of quang-host-answer-review Phase 5 — goes straight to {@code SCORED} +
 * emits {@code AnswerScored}; there is no host-approval hold anymore. A host's
 * own independent score, if any, is recorded separately via {@code
 * ScoringReviewService.submitTeacherScore} and never gates this.
 *
 * <p>Any exception here propagates to the container's retry advice
 * ({@code RabbitMqConfig}) — bounded retry with backoff, then dead-lettered;
 * {@link #onDeadLettered} marks the row {@code SCORING_FAILED} so a host sees
 * it, instead of it silently vanishing into the DLQ.
 */
@Component
public class AiScoringWorker {

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
                || answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED) {
            return; // Already terminal — redelivery no-op, do not re-call the vendor.
        }

        AiScoreResult result = callVendor(job);

        answer.markScored(result.rawScore());
        scoringAnswerRepository.save(answer);
        outboxWriter.write(ScoringConstants.AGGREGATE_ANSWER, answer.getAnswerPublicId().toString(),
                ScoringConstants.EVENT_ANSWER_SCORED,
                new AnswerScoredEvent(answer.getAttemptPublicId(), answer.getAnswerPublicId(),
                        answer.getTenantId(), result.rawScore()),
                answer.getTenantId());
        attemptCompletionService.checkAndEmitIfComplete(answer.getAttemptPublicId(), job.sessionPublicId(), answer.getTenantId());
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
