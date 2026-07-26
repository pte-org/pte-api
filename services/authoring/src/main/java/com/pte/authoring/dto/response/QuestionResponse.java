package com.pte.authoring.dto.response;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        UUID publicId,
        String pteTaskType,
        String section,
        String visibility,
        UUID tenantId,
        String status,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        List<OptionResponse> options,
        List<String> skills) {
}
