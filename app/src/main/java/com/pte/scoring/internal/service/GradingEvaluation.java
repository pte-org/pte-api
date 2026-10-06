package com.pte.scoring.internal.service;

import java.util.List;

public record GradingEvaluation(boolean complete, int requiredAttemptCount, int excludedAttemptCount,
        int expectedItemCount, int satisfiedItemCount, List<String> blockingReasons) {
    public GradingEvaluation {
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }
}
