package com.pte.scoring.internal.service.examiner.trait;

import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.examiner.StaleReviewVersionException;
import com.pte.scoring.internal.service.examiner.ExaminerReviewVersionService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Checks the same assignment/content/AI/publication projection the Examiner has reviewed. */
@Component
public class ExaminerTraitFreshness {
    private final ExaminerReviewVersionService versions;
    private final SessionGradingGateway gateway;
    private final AttemptService attempts;
    private final AnswerContentReader content;
    public ExaminerTraitFreshness(ExaminerReviewVersionService versions, SessionGradingGateway gateway,
            AttemptService attempts, AnswerContentReader content) {
        this.versions = versions; this.gateway = gateway; this.attempts = attempts; this.content = content;
    }
    public void requireCurrent(AiEvaluation answer, GradingAssignment assignment, String expected) {
        if (!com.pte.scoring.internal.constant.ExaminerScoringConstants.TRAIT_TASK_TYPES.contains(answer.getTaskType())) return;
        var tenant = answer.getTenantId(); var session = answer.getSessionPublicId();
        var cohort = gateway.grading(tenant, session).orElse(null);
        var member = cohort == null ? null : attempts.getGradingCandidate(answer.getAttemptPublicId(), tenant).orElse(null);
        var actual = versions.computeReviewVersion(answer, content.one(answer.getAnswerPublicId(), tenant).orElse(null),
                assignment, cohort, member, null, gateway.publication(tenant, session).orElse(null));
        if (!Objects.equals(expected, actual)) throw new StaleReviewVersionException();
    }
}
