package com.pte.scoring.dto.response;

import java.util.UUID;

/** Pool count derived from AI_SPEECH/AI_TEXT in each answer's pinned ScoreTemplate. */
public record AiEligibleAttemptView(UUID attemptPublicId, int eligibleAnswerCount) {
}
