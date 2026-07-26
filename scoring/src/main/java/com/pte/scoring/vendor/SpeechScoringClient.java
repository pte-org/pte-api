package com.pte.scoring.vendor;

/**
 * Scores a Read Aloud recording (fluency/pronunciation). {@code audioMediaPublicId}
 * is the media service's object reference — an implementation resolves it to
 * actual audio bytes via media's (not-yet-built, deferred until needed —
 * phase-09 scope cut) presigned download endpoint.
 */
public interface SpeechScoringClient {

    AiScoreResult score(String audioMediaPublicId, String referenceText);
}
