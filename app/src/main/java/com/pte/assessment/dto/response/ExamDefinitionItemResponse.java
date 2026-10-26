package com.pte.assessment.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.math.BigDecimal;

public record ExamDefinitionItemResponse(
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
        BigDecimal listeningWeight,
        TaskRuntimeProfileDescriptor runtime,
        String taskTypeKey) {

    public ExamDefinitionItemResponse(String taskType, String section, int sequence, int minCount, int maxCount,
            int prepSeconds, int responseSeconds, String scoringMethod, BigDecimal overallWeight,
            BigDecimal speakingWeight, BigDecimal writingWeight, BigDecimal readingWeight,
            BigDecimal listeningWeight) {
        this(taskType, section, sequence, minCount, maxCount, prepSeconds, responseSeconds, scoringMethod,
                overallWeight, speakingWeight, writingWeight, readingWeight, listeningWeight, null);
    }

    public ExamDefinitionItemResponse(String taskType, String section, int sequence, int minCount, int maxCount,
            int prepSeconds, int responseSeconds, String scoringMethod, BigDecimal overallWeight,
            BigDecimal speakingWeight, BigDecimal writingWeight, BigDecimal readingWeight,
            BigDecimal listeningWeight, TaskRuntimeProfileDescriptor runtime) {
        this(taskType, section, sequence, minCount, maxCount, prepSeconds, responseSeconds, scoringMethod,
                overallWeight, speakingWeight, writingWeight, readingWeight, listeningWeight, runtime, taskType);
    }
}
