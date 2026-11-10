package com.pte.scoring.dto.response;

public record ExaminerCommandResponse(
        ExaminerCommandReceiptResponse receipt,
        ExaminerWorkflowView current,
        ExaminerDraftResponse draft
,
        Integer itemScore, com.pte.scoring.rubric.ScoreTrace scoreTrace) {
    public ExaminerCommandResponse(ExaminerCommandReceiptResponse receipt,
        ExaminerWorkflowView current,
        ExaminerDraftResponse draft) {
        this(receipt, current, draft, null, null);
    }
}
