package com.pte.assessment;

import java.util.UUID;

/**
 * Service provided by the scoring module to the assessment module for pinning
 * the active rubric version when creating or cloning exam definitions.
 * Assessment does not import from scoring; the interface lives in assessment,
 * and scoring implements it.
 */
public interface ActiveRubricVersionPin {

    /**
     * @return the public ID of the currently ACTIVE rubric version, or empty if none.
     */
    java.util.Optional<UUID> activeRubricVersionPublicId();
}
