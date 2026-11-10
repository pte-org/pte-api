package com.pte.itembank.internal.generation.worker;

/** What a worker finds when it asks whether it still owns its job. */
public enum LeaseState {
    HELD, CANCEL_REQUESTED, LOST
}
