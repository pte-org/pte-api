package com.pte.scoring.support;

import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.internal.service.examiner.trait.ExaminerTraitScoreResolver;
import com.pte.scoring.internal.service.examiner.trait.ExaminerTraitScoreResult;
import java.util.Map;
import org.mockito.Mockito;

/** Existing workflow tests isolate rubric calculation, which is covered by the trait resolver tests. */
public final class ExaminerTraitWorkflowStub {
    private ExaminerTraitWorkflowStub() { }
    public static ExaminerTraitScoreResolver create() {
        return Mockito.mock(ExaminerTraitScoreResolver.class, invocation -> switch (invocation.getMethod().getName()) {
            case "resolve" -> ExaminerTraitScoreResult.legacy(invocation.getArgument(2));
            case "stored" -> ExaminerTraitScoreResult.legacy(((ExaminerEvaluation) invocation.getArgument(1)).getScore());
            case "schemas" -> Map.of();
            case "matchesAi" -> true;
            default -> null;
        });
    }
}
