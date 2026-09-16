package com.pte.reporting.internal.service;

/**
 * A single skill's (or Overall's) computed result. {@code sufficientData=false}
 * means no contributing scored answer existed — the caller must render
 * "insufficient data," never a fabricated 0.
 */
public record SkillScore(Integer score, boolean sufficientData) {

    public static SkillScore of(int score) {
        return new SkillScore(score, true);
    }

    public static SkillScore insufficientData() {
        return new SkillScore(null, false);
    }
}
