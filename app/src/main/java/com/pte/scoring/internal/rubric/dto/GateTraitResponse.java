package com.pte.scoring.internal.rubric.dto;

/** A gate trait: when enabled, a 0 on this trait zeroes the whole item. */
public record GateTraitResponse(String traitKey, boolean enabled) {
}
