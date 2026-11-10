package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.rubric.EffectiveRubric;

import java.util.UUID;

/** The effective rubric of one task type and the id of the stored rubric version it was built from. */
public record ResolvedRubric(UUID versionPublicId, EffectiveRubric rubric) {
}
