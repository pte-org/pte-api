package com.pte.session.internal.policy;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.internal.exception.InvalidLockdownModeException;

/** Resolves session lockdown policy at the session boundary. */
public final class SessionPolicyResolver {

    private SessionPolicyResolver() {
    }

    /** New canonical draft create: null is the OFFICIAL_EXAM default. */
    public static LockdownMode resolveForCreate(LockdownMode requested) {
        return requested == null ? LockdownMode.STRICT : validate(requested);
    }

    /**
     * Draft patch: a supplied lockdown value is validated; otherwise the
     * current effective policy is retained. {@code legacyState} marks a row
     * persisted before exam mode was recorded, whose null lockdown defaults.
     */
    public static LockdownMode resolveForDraftPatch(boolean legacyState, LockdownMode current,
            LockdownMode requestedLockdownMode) {
        if (requestedLockdownMode != null) {
            return validate(requestedLockdownMode);
        }
        return resolveEffectiveLockdownMode(ExamMode.OFFICIAL_EXAM, current, legacyState);
    }

    /** Null is deliberately a no-op for the partial policy endpoint. */
    public static LockdownMode resolveForPolicyPatch(LockdownMode current, LockdownMode requested) {
        if (requested == null) {
            return current;
        }
        return validate(requested);
    }

    /**
     * Resolves persisted state before it is exposed or pinned. Legacy null values
     * use the documented default; non-legacy null values fail closed.
     */
    public static LockdownMode resolveEffectiveLockdownMode(ExamMode examMode,
            LockdownMode persistedLockdownMode, boolean legacyState) {
        if (persistedLockdownMode == null) {
            if (!legacyState) {
                throw InvalidLockdownModeException.required();
            }
            return examMode == null ? LockdownMode.STANDARD : LockdownMode.STRICT;
        }
        // Rows created before exam_mode was introduced may carry the old
        // MOCK_TEST/STANDARD policy. Preserve that explicit legacy value; rows
        // with a known mode must still satisfy the current invariant.
        if (legacyState && examMode == null) {
            return persistedLockdownMode;
        }
        return validate(persistedLockdownMode);
    }

    private static LockdownMode validate(LockdownMode lockdownMode) {
        if (lockdownMode != LockdownMode.STRICT) {
            throw new InvalidLockdownModeException(
                    "OFFICIAL_EXAM sessions require LockdownMode.STRICT");
        }
        return lockdownMode;
    }
}
