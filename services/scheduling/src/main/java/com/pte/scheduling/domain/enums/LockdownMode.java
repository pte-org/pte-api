package com.pte.scheduling.domain.enums;

/**
 * Defines the exam lockdown enforcement level for desktop clients.
 * Pinned at StartAttempt; never re-fetched during an attempt's lifetime.
 */
public enum LockdownMode {
    /** No lockdown enforcement (PRACTICE mode default). */
    NONE,
    
    /** Warning-only mode - violations detected and reported but not blocked (MOCK_TEST default). */
    STANDARD,
    
    /** Strict enforcement - fullscreen mandatory, clipboard blocked, forbidden apps terminated (REAL_EXAM default). */
    STRICT
}
