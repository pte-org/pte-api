package com.pte.scoring.dto.response;

import java.util.Map;

/**
 * What lies behind one answer's AI score, for the host and the assigned
 * examiner only. {@code score} is the AI's estimate and is empty unless
 * {@code status} is SCORED; {@code failureReason} is a code, never a provider message.
 * Student and reporting responses must never carry this type.
 */
public record AiScoreDetailResponse(
        String status,
        Integer score,
        Map<String, Integer> criteria,
        String feedback,
        String transcript,
        Map<String, Object> detail,
        boolean fallback,
        String failureReason) {

    /** An AI-eligible answer the host has not queued for scoring yet. */
    public static AiScoreDetailResponse pending() {
        return new AiScoreDetailResponse("PENDING", null, Map.of(), null, null, Map.of(), false, null);
    }
}
