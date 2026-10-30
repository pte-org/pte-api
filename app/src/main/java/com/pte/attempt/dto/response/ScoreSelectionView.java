package com.pte.attempt.dto.response;

import java.time.Instant;
import java.util.UUID;

/** The score source the host chose for one answer, as stored on the answer. */
public record ScoreSelectionView(UUID answerPublicId, String source, Instant selectedAt, UUID selectedByPublicId,
                                 UUID evaluationPublicId) {
}
