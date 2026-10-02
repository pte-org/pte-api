package com.pte.session.internal.policy;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.internal.exception.InvalidLockdownModeException;

/** Resolves session lockdown policy at the session boundary. */
public final class SessionPolicyResolver {

    private SessionPolicyResolver() {
    }

    /** New canonical draft create: null is the compatibility default for the requested mode. */
    public static LockdownMode resolveForCreate(ExamMode examMode, LockdownMode requested) {
        ExamMode effectiveMode = effectiveExamMode(examMode);
        return validate(effectiveMode, requested == null ? defaultFor(effectiveMode) : requested);
    }

    /**
     * Draft patch semantics distinguish a mode transition from an unrelated patch:
     * a transition derives the new mode default, while an unchanged mode retains
     * the current effective policy when no lockdown value was supplied.
     */
    public static LockdownMode resolveForDraftPatch(ExamMode previousMode, LockdownMode current,
            ExamMode requestedMode, LockdownMode requestedLockdownMode) {
        ExamMode previous = effectiveExamMode(previousMode);
        ExamMode next = effectiveExamMode(requestedMode == null ? previous : requestedMode);
        if (requestedLockdownMode != null) {
            return validate(next, requestedLockdownMode);
        }
        if (previous != next) {
            return defaultFor(next);
        }
        return resolveEffectiveLockdownMode(next, current, previousMode == null);
    }

    /** Null is deliberately a no-op for the partial policy endpoint. */
    public static LockdownMode resolveForPolicyPatch(ExamMode examMode, LockdownMode current,
            LockdownMode requested) {
        if (requested == null) {
            return current;
        }
        return validate(effectiveExamMode(examMode), requested);
    }

    /**
     * Resolves persisted state before it is exposed or pinned. Legacy null values
     * use the documented mode-aware default; non-legacy null values fail closed.
     */
    public static LockdownMode resolveEffectiveLockdownMode(ExamMode examMode,
            LockdownMode persistedLockdownMode, boolean legacyState) {
        ExamMode effectiveMode = effectiveExamMode(examMode);
        if (persistedLockdownMode == null) {
            if (!legacyState) {
                throw InvalidLockdownModeException.required();
            }
            if (examMode == null) {
                return LockdownMode.STANDARD;
            }
            return defaultFor(effectiveMode);
        }
        // Rows created before exam_mode was introduced may carry the old
        // MOCK_TEST/STANDARD policy. Preserve that explicit legacy value; rows
        // with a known mode must still satisfy the current invariant.
        if (legacyState && examMode == null) {
            return persistedLockdownMode;
        }
        return validate(effectiveMode, persistedLockdownMode);
    }

    public static ExamMode effectiveExamMode(ExamMode examMode) {
        return examMode == null ? ExamMode.OFFICIAL_EXAM : examMode;
    }

    private static LockdownMode defaultFor(ExamMode examMode) {
        return examMode == ExamMode.PRACTICE ? LockdownMode.NONE : LockdownMode.STRICT;
    }

    private static LockdownMode validate(ExamMode examMode, LockdownMode lockdownMode) {
        if (examMode == ExamMode.OFFICIAL_EXAM && lockdownMode != LockdownMode.STRICT) {
            throw new InvalidLockdownModeException(
                    "OFFICIAL_EXAM sessions require LockdownMode.STRICT");
        }
        if (examMode == ExamMode.PRACTICE && lockdownMode == LockdownMode.STRICT) {
            throw new InvalidLockdownModeException(
                    "PRACTICE sessions cannot use LockdownMode.STRICT");
        }
        return lockdownMode;
    }
}
