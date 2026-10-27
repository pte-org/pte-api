package com.pte.shared.idempotency;

public record IdempotencyDecision(IdempotencyOutcome outcome, String resultJson) {

    static IdempotencyDecision created() {
        return new IdempotencyDecision(IdempotencyOutcome.NEW, null);
    }

    static IdempotencyDecision replay(String resultJson) {
        return new IdempotencyDecision(IdempotencyOutcome.REPLAY, resultJson);
    }

    static IdempotencyDecision conflict() {
        return new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null);
    }
}
