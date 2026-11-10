package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TreeMap;

/** Safe metadata projection; no score computation and no vendor access. */
@Component
public class ExaminerTraitResponses {
    private static final TypeReference<Map<String, String>> REASONS = new TypeReference<>() { };
    private final JsonMapper json;
    public ExaminerTraitResponses(JsonMapper json) { this.json = json; }
    public ExaminerDraftResponse draft(ExaminerEvaluation row) {
        return new ExaminerDraftResponse(row.getAnswerPublicId(), row.getDraftVersion(), row.getScore(),
                row.getComment(), row.getDecisionKind(), row.getExpectedReviewVersion(), row.getReviewedAiVersion(),
                row.getSavedAt(), row.isCarriedOver(), CriteriaScores.read(json, row.getTraitScoresJson()).traits(),
                row.getRubricVersionPublicId(), aiGapReasons(row.getAiGapReasonsJson()));
    }

    /** Stored form of the reasons: only traits the examiner scored, blank reasons dropped; null when none remain. */
    public String aiGapReasonsJson(Map<String, String> reasons, Map<String, ?> scoredTraits) {
        if (reasons == null || scoredTraits == null) {
            return null;
        }
        Map<String, String> kept = new TreeMap<>();
        reasons.forEach((key, reason) -> {
            if (scoredTraits.containsKey(key) && reason != null && !reason.isBlank()) {
                kept.put(key, reason.strip());
            }
        });
        return kept.isEmpty() ? null : json.writeValueAsString(kept);
    }

    /** Never fails: an unreadable value reads as no reasons. */
    public Map<String, String> aiGapReasons(String stored) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(stored, REASONS);
        } catch (RuntimeException unreadable) {
            return Map.of();
        }
    }
    public static com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse submission(ExaminerEvaluation row,
            String reviewVersion, ExaminerTraitScoreResult result) {
        var decision = new com.pte.scoring.dto.response.ExaminerDecisionResponse(row.getStatus().name(), row.getScore(),
                row.getDecisionKind(), row.getSubmittedAt(), row.getReviewedAiScore(), row.getReviewedAiVersion(),
                row.getRequestPublicId(), result.traits(), result.rubricVersionPublicId());
        return new com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse(row.getAnswerPublicId(), row.getScore(),
                row.getStatus().name(), row.getSubmittedAt(), decision, "EXAMINER", reviewVersion, result.itemScore(), result.scoreTrace());
    }
}
