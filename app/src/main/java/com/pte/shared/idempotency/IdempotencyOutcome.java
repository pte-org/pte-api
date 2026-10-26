package com.pte.shared.idempotency;

public enum IdempotencyOutcome {
    /** First use of the key: the caller executes the command. */
    NEW,
    /** Same key and request already completed: return the stored receipt. */
    REPLAY,
    /** Same key reused for a different request, or the first attempt has not finished. */
    CONFLICT
}
