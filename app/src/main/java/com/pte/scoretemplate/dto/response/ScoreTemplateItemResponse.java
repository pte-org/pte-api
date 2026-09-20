package com.pte.scoretemplate.dto.response;

import java.math.BigDecimal;

public record ScoreTemplateItemResponse(
        String taskType,
        String section,
        int sequence,
        int minCount,
        int maxCount,
        int prepSeconds,
        int responseSeconds,
        String scoringMethod,
        BigDecimal overallWeight,
        BigDecimal speakingWeight,
        BigDecimal writingWeight,
        BigDecimal readingWeight,
        BigDecimal listeningWeight) {
}
