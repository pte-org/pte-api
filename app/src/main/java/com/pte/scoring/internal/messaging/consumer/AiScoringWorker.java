package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.service.AiScoringTaskCatalog;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public AiScoringWorker(ScoringAnswerRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.speechScoringClient = speechScoringClient;
        this.essayScoringClient = essayScoringClient;
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
        if (AiScoringTaskCatalog.isSpeech(job.taskType())) {
            return speechScoringClient.score(job.payload(), job.referenceText(), job.tenantId());
        }
        if (AiScoringTaskCatalog.isText(job.taskType())) {
            return essayScoringClient.score(job.payload(), job.referenceText());
        }
        throw new IllegalArgumentException("Unsupported AI task type: " + job.taskType());
    }
}
