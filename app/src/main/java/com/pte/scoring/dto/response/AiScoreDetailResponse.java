package com.pte.scoring.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * What lies behind one answer's AI score, for the host and the assigned
 * examiner only. {@code score} is the AI's estimate and is empty unless
 * {@code status} is SCORED; {@code failureReason} is a code, never a provider message.
 * {@code criteriaScale} is {@code PEARSON_V1} when {@code criteria} are trait scores on the Pearson
 * scales of rubric version {@code rubricVersionPublicId}; both are null for older rows, whose criteria
 * are 0-100 scores. Student and reporting responses must never carry this type.
 */
public record AiScoreDetailResponse(
        String status,
        Integer score,
        Map<String, Integer> criteria,
        String feedback,
        String transcript,
        Map<String, Object> detail,
        boolean fallback,
        String failureReason,
        String criteriaScale,
        UUID rubricVersionPublicId) {

    /** A detail whose criteria are not tied to a rubric version. */
    public AiScoreDetailResponse(String status, Integer score, Map<String, Integer> criteria, String feedback,
            String transcript, Map<String, Object> detail, boolean fallback, String failureReason) {
        this(status, score, criteria, feedback, transcript, detail, fallback, failureReason, null, null);
    }

    /** An AI-eligible answer the host has not queued for scoring yet. */
    public static AiScoreDetailResponse pending() {
        return new AiScoreDetailResponse("PENDING", null, Map.of(), null, null, Map.of(), false, null);
    }
}
