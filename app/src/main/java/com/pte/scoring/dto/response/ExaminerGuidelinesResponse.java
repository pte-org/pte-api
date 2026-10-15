package com.pte.scoring.dto.response;

import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import java.util.List;
import java.util.UUID;

public record ExaminerGuidelinesResponse(
        int scaleMin,
        int scaleMax,
        String limitation,
        List<ExaminerTemplateGuidelineItem> templates
) {
    public record ExaminerTemplateGuidelineItem(
            UUID scoreTemplatePublicId,
            List<ScoreTemplateItemResponse> items
    ) {}
}
