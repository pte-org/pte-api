package com.pte.scoring.internal.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A host's independent score for one answer — same 0-100 percentage scale
 * objective/AI scoring already use, so it's directly comparable to {@code
 * rawScore}.
 */
public record SubmitTeacherScoreRequest(@Min(0) @Max(100) int score) {
}
