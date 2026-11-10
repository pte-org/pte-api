package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Echoes only the current Examiner's own saved value for safe client retry/resume. */
public record ExaminerScoreSubmissionResponse(
        UUID answerPublicId,
        int myScore,
        String status,
        Instant submittedAt,
        ExaminerDecisionResponse decision,
        String selectedScoreSource,
        String reviewVersion,
        Integer itemScore, com.pte.scoring.rubric.ScoreTrace scoreTrace) {
    public ExaminerScoreSubmissionResponse(UUID answerPublicId,
        int myScore,
        String status,
        Instant submittedAt,
        ExaminerDecisionResponse decision,
        String selectedScoreSource,
        String reviewVersion) {
        this(answerPublicId, myScore, status, submittedAt, decision, selectedScoreSource, reviewVersion, null, null);
    }


    public ExaminerScoreSubmissionResponse(UUID answerPublicId, int myScore, String status, Instant submittedAt) {
        this(answerPublicId, myScore, status, submittedAt, null, "EXAMINER", null);
    }
}
