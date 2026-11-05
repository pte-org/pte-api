package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import com.pte.scoring.internal.rubric.dto.RubricSimulationResponse;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only vendor sandbox. No exam/attempt data and no provider dependency, directly or through resolution. */
@Service
public class RubricSimulationService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ScoringRubricVersionRepository versions;
    private final RubricResolver resolver;

    public RubricSimulationService(ScoringRubricVersionRepository versions, RubricResolver resolver) {
        this.versions = versions;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public RubricSimulationResponse simulate(RubricSimulationRequest request) {
        if (JSON.writeValueAsString(request).getBytes(StandardCharsets.UTF_8).length
                > RubricConstants.MAX_SIMULATION_JSON_BYTES) {
            throw new RubricScoreException(RubricConstants.RUBRIC_PARAMETERS_TOO_LARGE);
        }
        if (request.traitScores() == null || request.traitScores().size() > RubricConstants.MAX_TRAIT_SCORE_ENTRIES) {
            throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_COUNT_INVALID);
        }
        if (request.traitScores().containsKey(RubricTraits.FORM)) {
            throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_UNKNOWN);
        }
        ScoringRubricVersion version = (request.rubricVersionPublicId() == null
                ? versions.findByStatus(RubricVersionStatus.ACTIVE)
                : versions.findByPublicId(request.rubricVersionPublicId()))
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NOT_FOUND));
        EffectiveRubric rubric = resolver.effectiveFor(version, request.taskType());
        Map<String, Integer> scores = exactScores(request);
        Integer formBand = null;
        if (rubric.effectiveFormRule() != null) {
            var metrics = request.textMetrics();
            if (metrics == null || metrics.wordCount() < 0 || metrics.sentenceCount() < 0) {
                throw new RubricScoreException(RubricConstants.RUBRIC_TEXT_METRICS_REQUIRED);
            }
            formBand = rubric.effectiveFormRule().bandFor(new TextMetrics(metrics.wordCount(), metrics.sentenceCount(),
                    metrics.allCaps(), metrics.punctuationPresent(), metrics.bulletOnly()));
            scores.put(RubricTraits.FORM, formBand);
        }
        ScoreTrace trace = RubricScorer.score(rubric, new TraitScoreSet(scores, request.wordCount()));
        return new RubricSimulationResponse(version.getPublicId(), trace.itemScore(), formBand, trace);
    }

    private static Map<String, Integer> exactScores(RubricSimulationRequest request) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        request.traitScores().forEach((key, value) -> {
            if (value == null) {
                throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_MISSING);
            }
            try {
                scores.put(key, value.intValueExact());
            } catch (ArithmeticException invalid) {
                throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
            }
        });
        return scores;
    }
}
