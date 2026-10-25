package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.service.ai.AiScoringResultPersistenceService;
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

    private final AiEvaluationRepository scoringAnswerRepository;
    private final SpeechScoringClient speechScoringClient;
    private final EssayScoringClient essayScoringClient;
    private final Map<String, AiAnswerScorer> scorersByTaskType;
    private final AiScoringResultPersistenceService resultPersistenceService;
    private final Clock clock;
    private final AiProviderProperties properties;
    private final AnswerContentReader contentReader;

    @Autowired
    public AiScoringWorker(AiEvaluationRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient, ObjectProvider<AiAnswerScorer> scorers,
                           AiScoringResultPersistenceService resultPersistenceService, Clock clock,
                           AiProviderProperties properties, AnswerContentReader contentReader) {
        this(scoringAnswerRepository, speechScoringClient, essayScoringClient, scorers.orderedStream().toList(),
                resultPersistenceService, clock, properties, contentReader);
    }

    /**
     * {@code scorers} are the task-specific scorers of the active provider; a task type none of them
     * names is scored by the generic speech or essay client.
     */
    public AiScoringWorker(AiEvaluationRepository scoringAnswerRepository, SpeechScoringClient speechScoringClient,
                           EssayScoringClient essayScoringClient, List<AiAnswerScorer> scorers,
                           AiScoringResultPersistenceService resultPersistenceService, Clock clock,
                           AiProviderProperties properties, AnswerContentReader contentReader) {
        this.contentReader = contentReader;
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
        AiEvaluation answer = scoringAnswerRepository.findByAnswerPublicId(job.answerPublicId())
                .filter(row -> row.isAwaitingAiResult(job.tenantId(), job.sessionPublicId(), job.attemptPublicId(),
                        token))
                .orElse(null);
        if (answer == null) {
            return;
        }
        if (resultPersistenceService.isPublicationLocked(job.tenantId(), job.sessionPublicId())) {
            return;
        }

        SubmittedAnswerView content = contentReader.one(job.answerPublicId(), job.tenantId()).orElse(null);
        if (content == null) {
            log.warn("AI scoring skipped for answer {}: the submitted answer is gone", job.answerPublicId());
            return;
        }
        AiScoreResult result;
        try {
            result = AiJobBudget.run(clock, Duration.ofMillis(properties.getJobBudgetMs()),
                    () -> callVendor(job.scoringMethod(), content));
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
    private AiScoreResult callVendor(String scoringMethod, SubmittedAnswerView answer) {
        AiAnswerScorer scorer = scorersByTaskType.get(answer.taskType());
        if (scorer != null) {
            return scorer.score(new AiScoringRequest(answer.taskType(), answer.payload(),
                    answer.promptText(), answer.referenceAnswerText(), answer.correctAnswerText(),
                    answer.imagePromptRef(), answer.minWordCount(), answer.maxWordCount(),
                    answer.tenantId()));
        }
        return switch (scoringMethod) {
            case "AI_SPEECH" -> speechScoringClient.score(answer.payload(), answer.correctAnswerText(),
                    answer.tenantId());
            case "AI_TEXT" -> essayScoringClient.score(answer.payload(), answer.correctAnswerText());
            default -> throw new IllegalArgumentException(
                    String.format(ScoringConstants.UNSUPPORTED_AI_TASK_TYPE, answer.taskType()));
        };
    }
}
