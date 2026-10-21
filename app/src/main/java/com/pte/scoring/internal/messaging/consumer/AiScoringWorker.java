package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.service.AiScoringResultPersistenceService;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes {@link AiScoringJob} from the RabbitMQ work queue. The job carries
 * identifiers and a dispatch token only; the candidate's answer is read from
 * the scoring row, and the worker acts only while that row still awaits the
 * job's token. Every AI-scored task type — {@code WRITE_ESSAY} included — goes
 * straight to {@code SCORED}; a host's own score is recorded separately via
 * {@code ScoringReviewService.submitTeacherScore} and never gates this.
 *
 * <p>Vendor calls run inside one {@link AiJobBudget}. Transient failures are
 * retried per HTTP call by the client's {@code ProviderGuard}, never by the
 * container — a container retry would call every vendor again and pay twice.
 * An {@link AiProviderException} that survives marks the answer failed with
 * its reason; any other exception rejects the message to the DLQ, where
 * {@link #onDeadLettered} marks it failed as {@code INTERNAL_ERROR}.
 */
@Component
public class AiScoringWorker {

    private static final Logger log = LoggerFactory.getLogger(AiScoringWorker.class);

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final SpeechScoringClient speechScoringClient;
    private final EssayScoringClient essayScoringClient;
    private final Map<String, AiAnswerScorer> scorersByTaskType;
    private final AiScoringResultPersistenceService resultPersistenceService;
    private final Clock clock;
    private final AiProviderProperties properties;

    @Autowired
    public AiScoringWorker(ScoringAnswerRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient, ObjectProvider<AiAnswerScorer> scorers,
                           AiScoringResultPersistenceService resultPersistenceService, Clock clock,
                           AiProviderProperties properties) {
        this(scoringAnswerRepository, speechScoringClient, essayScoringClient, scorers.orderedStream().toList(),
                resultPersistenceService, clock, properties);
    }

    /**
     * {@code scorers} are the task-specific scorers of the active provider; a task type none of them
     * names is scored by the generic speech or essay client.
     */
    public AiScoringWorker(ScoringAnswerRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient, List<AiAnswerScorer> scorers,
                           AiScoringResultPersistenceService resultPersistenceService, Clock clock,
                           AiProviderProperties properties) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.speechScoringClient = speechScoringClient;
        this.essayScoringClient = essayScoringClient;
        this.scorersByTaskType = byTaskType(scorers);
        this.resultPersistenceService = resultPersistenceService;
        this.clock = clock;
        this.properties = properties;
    }

    private static Map<String, AiAnswerScorer> byTaskType(List<AiAnswerScorer> scorers) {
        Map<String, AiAnswerScorer> table = new HashMap<>();
        for (AiAnswerScorer scorer : scorers) {
            for (String taskType : scorer.taskTypes()) {
                if (table.putIfAbsent(taskType, scorer) != null) {
                    throw new IllegalStateException(String.format(ScoringConstants.AI_SCORER_DUPLICATE, taskType));
                }
            }
        }
        return Map.copyOf(table);
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_QUEUE, containerFactory = "scoringRabbitListenerContainerFactory")
    public void onAiScoringJob(AiScoringJob job) {
        Long token = job.dispatchToken();
        if (token == null) {
            return; // Queued before dispatch tokens; the row is re-sent as stale on the host's next request.
        }
        // A terminal row or a newer dispatch both fail this check — redelivery never re-calls the vendor.
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(job.answerPublicId())
                .filter(row -> row.isAwaitingAiResult(job.tenantId(), job.sessionPublicId(), job.attemptPublicId(),
                        token))
                .orElse(null);
        if (answer == null) {
            return;
        }
        if (resultPersistenceService.isPublicationLocked(job.tenantId(), job.sessionPublicId())) {
            return;
        }

        AiScoreResult result;
        try {
            result = AiJobBudget.run(clock, Duration.ofMillis(properties.getJobBudgetMs()),
                    () -> callVendor(job.scoringMethod(), answer));
        } catch (AiProviderException ex) {
            log.warn("AI scoring failed for answer {}: {}", job.answerPublicId(), ex.getReason());
            resultPersistenceService.markScoringFailed(job.answerPublicId(), job.attemptPublicId(), job.tenantId(),
                    job.sessionPublicId(), token, ex.getReason());
            return;
        }
        resultPersistenceService.persistAiScore(job.answerPublicId(), job.attemptPublicId(), job.tenantId(),
                job.sessionPublicId(), token, result);
    }

    @RabbitListener(queues = ScoringConstants.AI_SCORING_DLQ, containerFactory = "scoringDeadLetterContainerFactory")
    public void onDeadLettered(AiScoringJob job) {
        if (job.dispatchToken() == null) {
            return;
        }
        resultPersistenceService.markScoringFailed(job.answerPublicId(), job.attemptPublicId(), job.tenantId(),
                job.sessionPublicId(), job.dispatchToken(), AiFailureReason.INTERNAL_ERROR);
    }

    /** Switches on the {@code scoringMethod} resolved once at dispatch time from the pinned ScoreTemplate (spec FR-07), not re-derived from task type here. */
    private AiScoreResult callVendor(String scoringMethod, ScoringAnswer answer) {
        AiAnswerScorer scorer = scorersByTaskType.get(answer.getTaskType());
        if (scorer != null) {
            return scorer.score(new AiScoringRequest(answer.getTaskType(), answer.getPayload(),
                    answer.getPromptText(), answer.getReferenceAnswerText(), answer.getCorrectAnswerText(),
                    answer.getImagePromptRef(), answer.getMinWordCount(), answer.getMaxWordCount(),
                    answer.getTenantId()));
        }
        return switch (scoringMethod) {
            case "AI_SPEECH" -> speechScoringClient.score(answer.getPayload(), answer.getCorrectAnswerText(),
                    answer.getTenantId());
            case "AI_TEXT" -> essayScoringClient.score(answer.getPayload(), answer.getCorrectAnswerText());
            default -> throw new IllegalArgumentException(
                    String.format(ScoringConstants.UNSUPPORTED_AI_TASK_TYPE, answer.getTaskType()));
        };
    }
}
