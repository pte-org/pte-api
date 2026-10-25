package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Practice adapter over the scorers the exam already uses. {@link #score} mirrors
 * {@code AiScoringWorker.callVendor}: the task-specific scorer when one is
 * registered, otherwise the generic speech or essay client, always inside one
 * {@link AiJobBudget}. It has no persistence or attempt side effects; Practice
 * owns the result snapshot and the dispatch bookkeeping. Keep the two in step.
 */
@Service
public class PracticeAiScoringService {

    private static final Logger log = LoggerFactory.getLogger(PracticeAiScoringService.class);
    private static final String OBJECTIVE_PROVIDER = "objective-scoring";
    private static final String OBJECTIVE_VERSION = "objective-scoring-v1";

    private final Map<String, AiAnswerScorer> scorersByTaskType;
    private final SpeechScoringClient speechScoringClient;
    private final EssayScoringClient essayScoringClient;
    private final ObjectiveScoringService objectiveScoringService;
    private final Clock clock;
    private final AiProviderProperties properties;

    @Autowired
    public PracticeAiScoringService(ObjectProvider<AiAnswerScorer> scorers, SpeechScoringClient speechScoringClient,
            EssayScoringClient essayScoringClient, ObjectiveScoringService objectiveScoringService, Clock clock,
            AiProviderProperties properties) {
        this(scorers.orderedStream().toList(), speechScoringClient, essayScoringClient, objectiveScoringService,
                clock, properties);
    }

    PracticeAiScoringService(List<AiAnswerScorer> scorers, SpeechScoringClient speechScoringClient,
            EssayScoringClient essayScoringClient, ObjectiveScoringService objectiveScoringService, Clock clock,
            AiProviderProperties properties) {
        this.scorersByTaskType = byTaskType(scorers);
        this.speechScoringClient = speechScoringClient;
        this.essayScoringClient = essayScoringClient;
        this.objectiveScoringService = objectiveScoringService;
        this.clock = clock;
        this.properties = properties;
    }

    /** Classifies a task type from the exam's runtime profile table; unknown codes are UNSUPPORTED. */
    public PracticeScoringRoute routeFor(String taskType) {
        return routeOf(runtimeFor(taskType));
    }

    /**
     * Scores one practice answer on the exam's 0-100 scale.
     *
     * @throws PracticeScoringException with a public reason when it cannot be scored
     */
    public PracticeAiEvaluation score(PracticeScoringRequest request) {
        if (request == null) {
            throw new PracticeScoringException(PracticeScoringFailure.INVALID_PAYLOAD);
        }
        String taskType = normalize(request.taskType());
        TaskRuntimeProfileDescriptor runtime = runtimeFor(taskType);
        PracticeScoringRoute route = routeOf(runtime);
        return switch (route) {
            case OBJECTIVE -> scoreObjective(taskType, runtime, request);
            case AI_SPEECH, AI_TEXT -> scoreAi(taskType, route, request);
            case UNSCORED, UNSUPPORTED -> throw new PracticeScoringException(
                    PracticeScoringFailure.UNSUPPORTED_TASK_TYPE);
        };
    }

    private PracticeAiEvaluation scoreObjective(String taskType, TaskRuntimeProfileDescriptor runtime,
            PracticeScoringRequest request) {
        try {
            SubmittedAnswerView answer = new SubmittedAnswerView(null, null, null, null, request.tenantId(), null,
                    taskType, request.payload(), request.correctAnswerText(), request.optionsJson(), false, null,
                    null, null, null, null, null, runtime, null, null, null, null);
            int rawScore = objectiveScoringService.score(answer);
            String hash = resultHash("objective-result", taskType, request, rawScore);
            return new PracticeAiEvaluation(rawScore, Map.of(), "UNVALIDATED", OBJECTIVE_PROVIDER, null,
                    OBJECTIVE_VERSION, null, null, clock.instant(), hash, "");
        } catch (RuntimeException exception) {
            log.warn("Practice objective scoring failed for task {}: {}", taskType,
                    exception.getClass().getSimpleName());
            throw new PracticeScoringException(PracticeScoringFailure.INTERNAL, exception);
        }
    }

    private PracticeAiEvaluation scoreAi(String taskType, PracticeScoringRoute route,
            PracticeScoringRequest request) {
        if (request.payload() == null || request.payload().isBlank()) {
            throw new PracticeScoringException(PracticeScoringFailure.INVALID_PAYLOAD);
        }
        AiScoringRequest aiRequest = new AiScoringRequest(taskType, request.payload(), request.promptText(),
                request.referenceAnswerText(), request.correctAnswerText(), request.imagePromptRef(),
                request.minWordCount(), request.maxWordCount(), request.tenantId());
        AiScoreResult result;
        try {
            result = AiJobBudget.run(clock, Duration.ofMillis(properties.getJobBudgetMs()),
                    () -> callVendor(route, aiRequest));
        } catch (AiProviderException exception) {
            log.warn("Practice AI scoring failed for task {}: {}", taskType, exception.getReason());
            throw new PracticeScoringException(failureOf(exception.getReason()), exception);
        } catch (RuntimeException exception) {
            log.warn("Practice AI scoring failed for task {}: {}", taskType, exception.getClass().getSimpleName());
            throw new PracticeScoringException(PracticeScoringFailure.INTERNAL, exception);
        }
        Instant evaluatedAt = clock.instant();
        String scoreStatus = result.fallback() ? "FALLBACK" : "UNVALIDATED";
        String hash = resultHash("practice-ai-result-v1", taskType, request, result.rawScore(),
                result.provider(), nullToEmpty(result.model()), nullToEmpty(result.providerVersion()),
                nullToEmpty(result.transcript()), nullToEmpty(result.detailJson()));
        return new PracticeAiEvaluation(result.rawScore(), result.subScores(), scoreStatus, result.provider(),
                result.model(), result.providerVersion(), result.transcript(), result.detailJson(), evaluatedAt,
                hash, result.feedback());
    }

    private AiScoreResult callVendor(PracticeScoringRoute route, AiScoringRequest request) {
        AiAnswerScorer scorer = scorersByTaskType.get(request.taskType());
        if (scorer != null) {
            return scorer.score(request);
        }
        return route == PracticeScoringRoute.AI_SPEECH
                ? speechScoringClient.score(request.payload(), request.correctAnswerText(), request.tenantId())
                : essayScoringClient.score(request.payload(), request.correctAnswerText());
    }

    private static TaskRuntimeProfileDescriptor runtimeFor(String taskType) {
        try {
            return TaskRuntimeProfileRegistry.descriptorFor(normalize(taskType));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static PracticeScoringRoute routeOf(TaskRuntimeProfileDescriptor runtime) {
        if (runtime == null) {
            return PracticeScoringRoute.UNSUPPORTED;
        }
        return ScoringProfileRegistry.resolve(runtime)
                .map(PracticeAiScoringService::routeOf)
                .orElse(PracticeScoringRoute.UNSUPPORTED);
    }

    private static PracticeScoringRoute routeOf(ScoringMethod method) {
        return switch (method) {
            case AI_SPEECH -> PracticeScoringRoute.AI_SPEECH;
            case AI_TEXT -> PracticeScoringRoute.AI_TEXT;
            case OBJECTIVE -> PracticeScoringRoute.OBJECTIVE;
            case UNSCORED -> PracticeScoringRoute.UNSCORED;
        };
    }

    private static PracticeScoringFailure failureOf(AiFailureReason reason) {
        return switch (reason) {
            case PROVIDER_RATE_LIMITED, PROVIDER_UNAVAILABLE, PROVIDER_NETWORK_ERROR ->
                    PracticeScoringFailure.PROVIDER_UNAVAILABLE;
            case PROVIDER_TIMEOUT -> PracticeScoringFailure.BUDGET_EXCEEDED;
            case PROVIDER_AUTH_FAILED, PROVIDER_BAD_REQUEST, PROVIDER_RESPONSE_INVALID,
                    PROVIDER_CONTENT_BLOCKED -> PracticeScoringFailure.PROVIDER_REJECTED;
            case AUDIO_UNSUPPORTED, AUDIO_TOO_LONG -> PracticeScoringFailure.INVALID_PAYLOAD;
            case INTERNAL_ERROR -> PracticeScoringFailure.INTERNAL;
        };
    }

    private static Map<String, AiAnswerScorer> byTaskType(List<AiAnswerScorer> scorers) {
        Map<String, AiAnswerScorer> table = new HashMap<>();
        for (AiAnswerScorer scorer : scorers) {
            for (String taskType : scorer.taskTypes()) {
                if (table.putIfAbsent(normalize(taskType), scorer) != null) {
                    throw new IllegalStateException("Duplicate practice AI scorer for " + normalize(taskType));
                }
            }
        }
        return Map.copyOf(table);
    }

    private static String normalize(String taskType) {
        return taskType == null ? "" : taskType.trim().toUpperCase(Locale.ROOT);
    }

    private static String resultHash(String version, String taskType, PracticeScoringRequest request,
            int rawScore, String... outcome) {
        String source = String.join("\n", version, taskType, nullToEmpty(request.payload()),
                nullToEmpty(request.promptText()), nullToEmpty(request.referenceAnswerText()),
                nullToEmpty(request.correctAnswerText()), nullToEmpty(request.optionsJson()),
                String.valueOf(rawScore), String.join("\n", outcome));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
