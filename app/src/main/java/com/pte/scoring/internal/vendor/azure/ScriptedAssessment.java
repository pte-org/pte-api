package com.pte.scoring.internal.vendor.azure;

/**
 * Both passes over a scripted recording: what recognition alone heard (used to
 * measure completeness, since the scored pass is forced onto the reference)
 * and the pronunciation scores of the pass against the reference.
 */
public record ScriptedAssessment(SpeechAssessment heard, SpeechAssessment assessed) {
}
