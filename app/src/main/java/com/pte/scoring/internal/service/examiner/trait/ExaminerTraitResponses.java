package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Safe metadata projection; no score computation and no vendor access. */
@Component
public class ExaminerTraitResponses {
    private final JsonMapper json;
    public ExaminerTraitResponses(JsonMapper json) { this.json = json; }
    public ExaminerDraftResponse draft(ExaminerEvaluation row) {
        return new ExaminerDraftResponse(row.getAnswerPublicId(), row.getDraftVersion(), row.getScore(),
                row.getComment(), row.getDecisionKind(), row.getExpectedReviewVersion(), row.getReviewedAiVersion(),
                row.getSavedAt(), row.isCarriedOver(), CriteriaScores.read(json, row.getTraitScoresJson()).traits(),
                row.getRubricVersionPublicId());
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
