package com.pte.scoring.internal.service.review;

import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.internal.constant.ScoringConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Reads what an explanation needs from a stored AI result: the Pearson trait scores, the rubric version they were
 * scored with, and the word count stored in the detail. Only rows with the {@code PEARSON_V1} scale yield traits; a
 * legacy 0-100 row or an unreadable one yields none, so it is shown as "old data, no trace" and never rescaled.
 */
@Component
public class ReportTraitReader {

    private final JsonMapper json;

    public ReportTraitReader(JsonMapper json) {
        this.json = json;
    }

    public StoredTraits read(AiEvaluation answer) {
        CriteriaScores criteria = CriteriaScores.read(json, answer.getCriteriaScoresJson());
        if (!criteria.isPearson() || criteria.traits().isEmpty()) {
            return StoredTraits.NONE;
        }
        UUID version = answer.getRubricVersionPublicId() != null ? answer.getRubricVersionPublicId()
                : criteria.rubricVersion();
        return new StoredTraits(criteria.traits(), version, wordCount(answer.getDetailJson()));
    }

    public StoredTraits readExaminer(com.pte.scoring.domain.ExaminerEvaluation examiner, AiEvaluation answer) {
        if (examiner == null) return StoredTraits.NONE;
        CriteriaScores criteria = CriteriaScores.read(json, examiner.getTraitScoresJson());
        if (!criteria.isPearson() || examiner.getRubricVersionPublicId() == null) return StoredTraits.NONE;
        Integer frozenWords = wordCount(examiner.getTraitScoresJson());
        return new StoredTraits(criteria.traits(), examiner.getRubricVersionPublicId(),
                frozenWords == null ? wordCount(answer.getDetailJson()) : frozenWords);
    }

    private Integer wordCount(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            JsonNode count = json.readTree(detailJson).get(ScoringConstants.DETAIL_WORD_COUNT);
            return count != null && count.isIntegralNumber() ? count.asInt() : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Trait scores on the Pearson scale (empty for legacy/objective rows), their rubric version and word count. */
    public record StoredTraits(Map<String, Integer> traits, UUID rubricVersionPublicId, Integer wordCount) {

        static final StoredTraits NONE = new StoredTraits(Map.of(), null, null);
    }
}
