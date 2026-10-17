package com.pte.reporting.internal.service;

import java.math.BigDecimal;

/**
 * A single skill's (or Overall's) computed result. {@code sufficientData=false}
 * means no contributing scored answer existed — the caller must render
 * "insufficient data," never a fabricated 0. {@code appliedWeight} is the
 * sum of the pinned template weights that actually contributed to this result;
 * zero means the result has no data, while {@code null} is retained for legacy
 * callers/snapshots that predate the breakdown field.
 */
public record SkillScore(Integer score, boolean sufficientData, BigDecimal appliedWeight) {

    /** Backward-compatible constructor for legacy callers and snapshots. */
    public SkillScore(Integer score, boolean sufficientData) {
        this(score, sufficientData, null);
    }

    public static SkillScore of(int score) {
        return new SkillScore(score, true, null);
    }

    public static SkillScore of(int score, BigDecimal appliedWeight) {
        return new SkillScore(score, true, appliedWeight);
    }

    public static SkillScore insufficientData() {
        return new SkillScore(null, false, null);
    }

    public static SkillScore insufficientData(BigDecimal appliedWeight) {
        return new SkillScore(null, false, appliedWeight);
    }
}
