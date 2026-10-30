package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.dto.response.timing.PracticeItemTimingResponse;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;

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
        PracticeResponseStatus status,
        String savedPayload,
        ResponseConfidence confidence,
        PracticeTaskContentResponse content,
        PracticeItemTimingResponse timing) {

    /** Compatibility constructor for callers that only need task metadata. */
    public PracticeTaskResponse(UUID publicId, int orderIndex, String taskCode, String displayName,
            String section, String rendererKey, Integer contractVersion, Integer answerSchemaVersion,
            PracticeResponseStatus status, String savedPayload, ResponseConfidence confidence) {
        this(publicId, orderIndex, taskCode, displayName, section, rendererKey, contractVersion,
                answerSchemaVersion, status, savedPayload, confidence, null, null);
    }

    /** Compatibility constructor for callers that predate per-item timing. */
    public PracticeTaskResponse(UUID publicId, int orderIndex, String taskCode, String displayName,
            String section, String rendererKey, Integer contractVersion, Integer answerSchemaVersion,
            PracticeResponseStatus status, String savedPayload, ResponseConfidence confidence,
            PracticeTaskContentResponse content) {
        this(publicId, orderIndex, taskCode, displayName, section, rendererKey, contractVersion,
                answerSchemaVersion, status, savedPayload, confidence, content, null);
    }
}
