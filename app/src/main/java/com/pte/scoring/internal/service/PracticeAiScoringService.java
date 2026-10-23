package com.pte.scoring.internal.service;

import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous practice adapter for the provider already used by exam scoring.
 * It intentionally has no persistence or attempt side effects; Practice owns
 * the response snapshot while scoring owns provider selection and provenance.
 */
@Service
public class PracticeAiScoringService {

    private static final Logger log = LoggerFactory.getLogger(PracticeAiScoringService.class);

    private final Map<String, AiAnswerScorer> scorersByTaskType;
    private final Clock clock;

    @Autowired
    public PracticeAiScoringService(ObjectProvider<AiAnswerScorer> scorers, Clock clock) {
        this(scorers.orderedStream().toList(), clock);
    }

    PracticeAiScoringService(List<AiAnswerScorer> scorers, Clock clock) {
        this.scorersByTaskType = byTaskType(scorers);
        this.clock = clock;
    }

    public Optional<PracticeAiEvaluation> evaluateSpeech(String taskType, String audioMediaPublicId,
            String promptText, String referenceAnswerText, UUID tenantId) {
        AiAnswerScorer scorer = scorersByTaskType.get(normalize(taskType));
        if (scorer == null || audioMediaPublicId == null || audioMediaPublicId.isBlank()) {
            return Optional.empty();
        }
        try {
            AiScoreResult result = scorer.score(new AiScoringRequest(normalize(taskType), audioMediaPublicId,
                    promptText, referenceAnswerText, null, null, null, null, tenantId));
            Instant evaluatedAt = clock.instant();
            String scoreStatus = result.fallback() ? "FALLBACK" : "UNVALIDATED";
            return Optional.of(new PracticeAiEvaluation(result.rawScore(), result.subScores(), scoreStatus,
                    result.provider(), result.model(), result.providerVersion(), result.transcript(), result.detailJson(), evaluatedAt,
                    resultHash(taskType, audioMediaPublicId, promptText, referenceAnswerText, result)));
        } catch (RuntimeException exception) {
            log.warn("Practice AI scoring failed for task {}: {}", normalize(taskType),
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
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

    private static String resultHash(String taskType, String audioMediaPublicId, String promptText,
            String referenceAnswerText, AiScoreResult result) {
        String source = String.join("\n", "practice-ai-result-v1", normalize(taskType), audioMediaPublicId,
                nullToEmpty(promptText), nullToEmpty(referenceAnswerText), String.valueOf(result.rawScore()),
                result.provider(), nullToEmpty(result.model()), nullToEmpty(result.providerVersion()),
                nullToEmpty(result.transcript()), nullToEmpty(result.detailJson()));
        try {
            return HexFormatHolder.HEX.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class HexFormatHolder {
        private static final java.util.HexFormat HEX = java.util.HexFormat.of();
    }
}
