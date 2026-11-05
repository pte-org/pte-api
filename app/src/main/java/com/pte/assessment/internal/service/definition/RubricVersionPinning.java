package com.pte.assessment.internal.service.definition;

import com.pte.assessment.ActiveRubricVersionPin;
import com.pte.assessment.internal.exception.NoActiveRubricVersionException;

import java.util.UUID;

/**
 * Chooses the rubric version a NEW exam definition pins (FR-02). Existing definitions are never re-pinned: activation,
 * retirement and edits leave {@code rubricVersionPublicId} alone. Every new definition gets a non-null pin; without
 * one, creation fails instead of storing an unpinned row (the DB column stays nullable for legacy rows only).
 */
final class RubricVersionPinning {

    /** Null only in compatibility constructors; then nothing can be pinned and creation fails closed. */
    private final ActiveRubricVersionPin activeRubricVersionPin;

    RubricVersionPinning(ActiveRubricVersionPin activeRubricVersionPin) {
        this.activeRubricVersionPin = activeRubricVersionPin;
    }

    /** A brand-new definition (vendor draft or vendor clone into a new version) pins the ACTIVE rubric version. */
    UUID active() {
        if (activeRubricVersionPin == null) {
            throw new NoActiveRubricVersionException();
        }
        return activeRubricVersionPin.activeRubricVersionPublicId().orElseThrow(NoActiveRubricVersionException::new);
    }

    /** A host copy keeps the pin of the template it copies; a legacy source without one falls back to ACTIVE. */
    UUID inheritedFrom(UUID sourcePin) {
        return sourcePin != null ? sourcePin : active();
    }
}
