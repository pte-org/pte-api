package com.pte.attempt.domain.enums;

/**
 * Signals emitted by the student desktop lockdown client. This vocabulary is
 * intentionally separate from the manual proctor violation vocabulary.
 */
public enum LockdownViolationType {
    LOCKDOWN_FULLSCREEN_EXIT,
    LOCKDOWN_CLIPBOARD_PASTE,
    LOCKDOWN_SHORTCUT_BLOCKED,
    LOCKDOWN_FORBIDDEN_APP_DETECTED
}
