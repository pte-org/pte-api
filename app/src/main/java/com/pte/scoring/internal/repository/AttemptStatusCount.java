package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.enums.ScoringAnswerStatus;

import java.util.UUID;

/** Answer count of one attempt in one status — the raw rows the host's per-attempt review rolls up. */
public record AttemptStatusCount(UUID attemptPublicId, ScoringAnswerStatus status, long count) {
}
