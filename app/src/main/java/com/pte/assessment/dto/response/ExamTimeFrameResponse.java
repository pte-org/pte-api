package com.pte.assessment.dto.response;

/** Pearson time window of a template; every value is in seconds. */
public record ExamTimeFrameResponse(
        int speakingWritingMinSeconds,
        int speakingWritingMaxSeconds,
        int readingMinSeconds,
        int readingMaxSeconds,
        int listeningMinSeconds,
        int listeningMaxSeconds,
        int totalMaxSeconds) {
}
