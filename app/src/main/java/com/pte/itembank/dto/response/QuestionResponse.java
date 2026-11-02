package com.pte.itembank.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        UUID publicId,
        String pteTaskType,
        String section,
        UUID tenantId,
        String status,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        List<OptionResponse> options,
        UUID revisionGroupPublicId,
        int revisionNumber,
        UUID supersedesPublicId,
        boolean current,
        long version,
        String rejectionReason,
        String taskTypeKey,
        boolean canDeleteDraft,
        boolean canArchive,
        String deleteBlockReason,
        String pool,
        boolean availableForPractice,
        boolean availableForExam,
        /** True when this question or an earlier revision in its group was ever published. */
        boolean previouslyPublished,
        /** When the question last changed; tracking screens show how long a question has waited. */
        Instant updatedAt) {
}
