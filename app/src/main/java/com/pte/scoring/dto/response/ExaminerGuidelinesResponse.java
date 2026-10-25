package com.pte.scoring.dto.response;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import java.util.List;
import java.util.UUID;

public record ExaminerGuidelinesResponse(
        int scaleMin,
        int scaleMax,
        String limitation,
        List<ExaminerTemplateGuidelineItem> templates
) {
    public record ExaminerTemplateGuidelineItem(
            UUID examDefinitionPublicId,
            List<ExamDefinitionItemResponse> items
    ) {}
}
