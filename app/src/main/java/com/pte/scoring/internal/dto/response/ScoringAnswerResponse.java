package com.pte.scoring.internal.dto.response;

import java.util.UUID;

public record ScoringAnswerResponse(UUID answerPublicId, UUID attemptPublicId, String taskType, String status,
                                     Integer rawScore, Integer teacherScore) {
}
