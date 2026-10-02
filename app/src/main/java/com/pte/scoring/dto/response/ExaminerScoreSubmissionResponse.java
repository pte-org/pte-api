package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Echoes only the current Examiner's own saved value for safe client retry/resume. */
public record ExaminerScoreSubmissionResponse(
        UUID answerPublicId,
        int myScore,
        String status,
        Instant submittedAt) {
}
