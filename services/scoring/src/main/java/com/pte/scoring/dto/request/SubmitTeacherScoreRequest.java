package com.pte.scoring.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A host's independent score for one answer (quang-host-answer-review Phase 5)
 * — same 0-100 percentage scale {@code ObjectiveScoringService}/AI scoring
 * already use, so it's directly comparable to {@code rawScore}.
 */
public record SubmitTeacherScoreRequest(@Min(0) @Max(100) int score) {
}
