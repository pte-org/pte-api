package com.pte.scoring.internal.service;

import com.pte.scoring.internal.repository.StaffAssignmentRow;
import com.pte.scoring.internal.repository.StaffScoreRow;
import com.pte.scoring.internal.repository.StaffAnswerRow;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Bounded-batch accumulator, never exposes answer payloads or scores. */
final class StaffAttemptProgress {
    static final String ASSIGNMENT_INVALID = "ASSIGNMENT_DATA_INVALID";
    static final String TEMPLATE_UNAVAILABLE = "ELIGIBILITY_TEMPLATE_UNAVAILABLE";
    static final String COUNT_MISMATCH = "ELIGIBLE_COUNT_MISMATCH";
    static final String SCORE_INVALID = "SUBMITTED_SCORE_DATA_INVALID";
    final StaffAssignmentRow assignment;
    final java.util.UUID templateId;
    long expectedEligible;
    final Set<String> diagnostics = new LinkedHashSet<>();
    long eligible;
    long submitted;
    long matchedScoreRows;

    StaffAttemptProgress(StaffAssignmentRow assignment, java.util.UUID templateId) {
        this.assignment = assignment;
        this.templateId = templateId;
        if (assignment.getEligibleAnswerCount() < 0 || assignment.getBatchPublicId() == null
                || assignment.getAssignedAt() == null || assignment.getAttemptPublicId() == null) {
            diagnostics.add(ASSIGNMENT_INVALID);
        }
    }

    void accept(StaffAnswerRow answer, boolean isEligible, java.util.List<StaffScoreRow> scores) {
        if (!Objects.equals(answer.getSessionPublicId(), assignment.getSessionPublicId())) {
            diagnostics.add(ASSIGNMENT_INVALID);
            return;
        }
        if (!Objects.equals(answer.getScoreTemplatePublicId(), templateId)) {
            diagnostics.add(TEMPLATE_UNAVAILABLE);
            return;
        }
        if (isEligible) { eligible++; }
        for (StaffScoreRow score : scores) {
            if (!isEligible || !Objects.equals(score.getExaminerPublicId(), assignment.getExaminerPublicId())
                    || !Objects.equals(score.getAttemptPublicId(), assignment.getAttemptPublicId())
                    || !Objects.equals(score.getSessionPublicId(), assignment.getSessionPublicId())
                    || score.getStatus() != ExaminerAnswerScoreStatus.SUBMITTED || score.getSubmittedAt() == null
                    || score.getScore() < 0 || score.getScore() > 100 || scores.size() != 1) {
                diagnostics.add(SCORE_INVALID);
            } else {
                submitted++;
                matchedScoreRows++;
            }
        }
    }

    String finish(long scoreRows) {
        if (eligible != assignment.getEligibleAnswerCount() || eligible != expectedEligible) {
            diagnostics.add(COUNT_MISMATCH);
        }
        if (scoreRows != matchedScoreRows) { diagnostics.add(SCORE_INVALID); }
        if (!diagnostics.isEmpty()) { return "UNAVAILABLE"; }
        if (eligible == submitted) { return "COMPLETED"; }
        return submitted == 0 ? "PENDING" : "IN_PROGRESS";
    }
}
