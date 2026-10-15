package com.pte.scoring.dto.response;

public record ExaminerCommandResponse(
        ExaminerCommandReceiptResponse receipt,
        ExaminerWorkflowView current,
        ExaminerDraftResponse draft
) {}
