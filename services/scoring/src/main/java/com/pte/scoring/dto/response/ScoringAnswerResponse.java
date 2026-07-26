package com.pte.scoring.dto.response;

import java.util.UUID;

public record ScoringAnswerResponse(UUID answerPublicId, UUID attemptPublicId, String taskType, String status, Integer rawScore) {
}
