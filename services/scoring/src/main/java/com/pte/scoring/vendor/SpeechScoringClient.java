package com.pte.scoring.vendor;

import java.util.UUID;

/**
 * Scores a Read Aloud recording (fluency/pronunciation). {@code audioMediaPublicId}
 * is the media service's object reference — an implementation resolves it to
 * actual audio bytes via media's tenant-scoped presigned download endpoint.
 */
public interface SpeechScoringClient {

    AiScoreResult score(String audioMediaPublicId, String referenceText, UUID tenantId);
}
