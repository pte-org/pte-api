package com.pte.shared.practice;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module port: which student recordings a practice answer still uses. The media module asks it before removing
 * a recording, so the practice module keeps the rule for what "still used" means and media never reads practice data.
 */
public interface PracticeMediaReferencePort {

    /**
     * Of the given recordings (media public id to the practice item it was uploaded for), those whose item still
     * holds them in its saved answer, in a session that was not discarded.
     */
    Set<UUID> findReferencedResponseAudio(Map<UUID, UUID> itemPublicIdByMediaPublicId);
}
