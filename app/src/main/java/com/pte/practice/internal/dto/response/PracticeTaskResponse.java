package com.pte.practice.internal.dto.response;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;

import java.util.UUID;

/** Student-safe projection of the current task contract in a practice session. */
public record PracticeTaskResponse(
        UUID publicId,
        int orderIndex,
        String taskCode,
        String displayName,
        String section,
        String rendererKey,
        Integer contractVersion,
        Integer answerSchemaVersion,
        PracticeSessionItemStatus status,
        String savedPayload,
        ResponseConfidence confidence) {
}
