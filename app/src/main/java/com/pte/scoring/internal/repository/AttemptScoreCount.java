package com.pte.scoring.internal.repository;

import java.util.UUID;

/** Submitted examiner scores of one attempt, for attempts graded before the draft workflow existed. */
public record AttemptScoreCount(UUID attemptPublicId, long count) {
}
