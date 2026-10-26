package com.pte.scoring.internal.service.grading;

import com.pte.scoring.internal.service.selection.ScoreSelectionReader;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.GradingConstants;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Evaluates every frozen pinned item against its required scoring lane. */
@Service
public class GradingCompletionEvaluator {

    public GradingEvaluation evaluate(GradingMarkingMode markingMode, List<AttemptGradingCandidateView> members,
            Map<UUID, AttemptGradingCoverageView> coverageByAttempt, List<AiEvaluation> answers,
            List<GradingAssignment> assignments, List<ExaminerEvaluation> examinerScores,
            Map<UUID, ScoreSelectionReader.Selection> selections) {
        Map<UUID, Map<UUID, AiEvaluation>> answersByAttempt = answers.stream()
                .collect(Collectors.groupingBy(AiEvaluation::getAttemptPublicId, HashMap::new,
                        Collectors.toMap(AiEvaluation::getItemPublicId, Function.identity(), (left, right) -> left)));
        Map<UUID, GradingAssignment> assignmentByAttempt = assignments.stream()
                .collect(Collectors.toMap(GradingAssignment::getAttemptPublicId, Function.identity(),
                        (left, right) -> left));
        Map<UUID, ExaminerEvaluation> scoresByAnswer = examinerScores.stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity(), (left, right) -> left));
        Set<String> reasons = new LinkedHashSet<>();
        int requiredAttempts = 0;
        int excludedAttempts = 0;
        int expectedItems = 0;
        int satisfiedItems = 0;

        for (AttemptGradingCandidateView member : members) {
            if (member.excluded()) {
                excludedAttempts++;
                continue;
            }
            requiredAttempts++;
            AttemptGradingCoverageView coverage = coverageByAttempt.get(member.attemptPublicId());
            if (coverage == null || coverage.expectedItems().isEmpty()) {
                reasons.add(GradingConstants.PINNED_COVERAGE_MISSING);
                continue;
            }
            Set<UUID> seenItems = new HashSet<>();
            Map<UUID, AiEvaluation> attemptAnswers = answersByAttempt.getOrDefault(member.attemptPublicId(), Map.of());
            int subjectiveItems = 0;
            for (AttemptGradingItemView item : coverage.expectedItems()) {
                expectedItems++;
                if (item.itemPublicId() == null || !seenItems.add(item.itemPublicId())) {
                    reasons.add(GradingConstants.PINNED_COVERAGE_MISSING);
                    continue;
                }
                AiEvaluation answer = attemptAnswers.get(item.itemPublicId());
                if (answer == null) {
                    reasons.add(GradingConstants.MISSING_EXPECTED_ANSWER);
                    continue;
                }
                ScoringMethod method = answer.getScoringMethod();
                if (method == null) {
                    reasons.add(GradingConstants.SCORING_METHOD_UNAVAILABLE);
                    continue;
                }
                if (method == ScoringMethod.UNSCORED) {
                    satisfiedItems++;
                    continue;
                }
                if (method == ScoringMethod.OBJECTIVE) {
                    if (answer.getStatus() == ScoringAnswerStatus.SCORED && validScore(answer.getRawScore())) {
                        satisfiedItems++;
                    } else {
                        reasons.add(GradingConstants.OBJECTIVE_SCORE_REQUIRED);
                    }
                    continue;
                }
                subjectiveItems++;
                if (markingMode == GradingMarkingMode.MANUAL_EXAMINER) {
                    ExaminerEvaluation score = scoresByAnswer.get(answer.getAnswerPublicId());
                    GradingAssignment assignment = assignmentByAttempt.get(member.attemptPublicId());
                    if (assignment == null || assignment.getEligibleAnswerCount() < subjectiveItems) {
                        reasons.add(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);
                    } else if (score == null || !score.isPublishable()
                            || !java.util.Objects.equals(score.getExaminerPublicId(), assignment.getExaminerPublicId())) {
                        reasons.add(GradingConstants.EXAMINER_SCORE_REQUIRED);
                    } else if (selectedElsewhere(selections.get(answer.getAnswerPublicId()), score)) {
                        reasons.add(GradingConstants.EXAMINER_SCORE_REQUIRED);
                    } else {
                        satisfiedItems++;
                    }
                } else if (answer.hasPublishableAiScore()) {
                    satisfiedItems++;
                } else {
                    reasons.add(GradingConstants.AI_SCORE_REQUIRED);
                }
            }
        }
        if (requiredAttempts == 0) {
            reasons.add(GradingConstants.NO_PAPERS);
        }
        return new GradingEvaluation(reasons.isEmpty(), requiredAttempts, excludedAttempts, expectedItems,
                satisfiedItems, List.copyOf(reasons));
    }

    /** The host chose AI for this answer, or a different examiner score than the one that counts here. */
    private boolean selectedElsewhere(ScoreSelectionReader.Selection selection, ExaminerEvaluation score) {
        if (selection == null || selection.source() == null) {
            return false;
        }
        return selection.source() != com.pte.scoring.domain.enums.ScoreSource.EXAMINER
                || selection.evaluationPublicId() != null && score.getPublicId() != null
                    && !java.util.Objects.equals(selection.evaluationPublicId(), score.getPublicId());
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }
}
