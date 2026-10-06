package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
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

    private final ScoringMethodResolver methodResolver;

    public GradingCompletionEvaluator(ScoringMethodResolver methodResolver) {
        this.methodResolver = methodResolver;
    }

    public GradingEvaluation evaluate(GradingMarkingMode markingMode, List<GradingCohortMember> members,
            Map<UUID, AttemptGradingCoverageView> coverageByAttempt, List<ScoringAnswer> answers,
            List<ExaminerAttemptAssignment> assignments, List<ExaminerAnswerScore> examinerScores) {
        Map<UUID, Map<UUID, ScoringAnswer>> answersByAttempt = answers.stream()
                .collect(Collectors.groupingBy(ScoringAnswer::getAttemptPublicId, HashMap::new,
                        Collectors.toMap(ScoringAnswer::getPinnedItemPublicId, Function.identity(), (left, right) -> left)));
        Map<UUID, ExaminerAttemptAssignment> assignmentByAttempt = assignments.stream()
                .collect(Collectors.toMap(ExaminerAttemptAssignment::getAttemptPublicId, Function.identity(),
                        (left, right) -> left));
        Map<UUID, ExaminerAnswerScore> scoresByAnswer = examinerScores.stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity(), (left, right) -> left));
        Set<String> reasons = new LinkedHashSet<>();
        int requiredAttempts = 0;
        int excludedAttempts = 0;
        int expectedItems = 0;
        int satisfiedItems = 0;

        for (GradingCohortMember member : members) {
            if (member.isExcluded()) {
                excludedAttempts++;
                continue;
            }
            requiredAttempts++;
            AttemptGradingCoverageView coverage = coverageByAttempt.get(member.getAttemptPublicId());
            if (coverage == null || coverage.expectedItems().isEmpty()) {
                reasons.add(GradingConstants.PINNED_COVERAGE_MISSING);
                continue;
            }
            Set<UUID> seenItems = new HashSet<>();
            Map<UUID, ScoringAnswer> attemptAnswers = answersByAttempt.getOrDefault(member.getAttemptPublicId(), Map.of());
            int subjectiveItems = 0;
            for (AttemptGradingItemView item : coverage.expectedItems()) {
                expectedItems++;
                if (item.pinnedItemPublicId() == null || !seenItems.add(item.pinnedItemPublicId())) {
                    reasons.add(GradingConstants.PINNED_COVERAGE_MISSING);
                    continue;
                }
                ScoringAnswer answer = attemptAnswers.get(item.pinnedItemPublicId());
                if (answer == null) {
                    reasons.add(GradingConstants.MISSING_EXPECTED_ANSWER);
                    continue;
                }
                ScoringMethod method;
                try {
                    method = methodResolver.resolve(item.scoreTemplatePublicId(), item.taskType()).orElse(null);
                } catch (RuntimeException ex) {
                    method = null;
                }
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
                    ExaminerAnswerScore score = scoresByAnswer.get(answer.getAnswerPublicId());
                    ExaminerAttemptAssignment assignment = assignmentByAttempt.get(member.getAttemptPublicId());
                    if (assignment == null || assignment.getEligibleAnswerCount() < subjectiveItems) {
                        reasons.add(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);
                    } else if (score == null || !score.isPublishable()) {
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

    private boolean validScore(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }
}
