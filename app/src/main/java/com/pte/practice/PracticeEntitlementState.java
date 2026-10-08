package com.pte.practice;

/** Server-derived practice access state; the browser must not derive this. */
public enum PracticeEntitlementState {
    UNLOCKED,
    LOCKED,
    AMBIGUOUS,
    UNAVAILABLE
}
