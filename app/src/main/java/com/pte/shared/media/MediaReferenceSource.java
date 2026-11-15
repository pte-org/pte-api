package com.pte.shared.media;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * A module that keeps question media (a prompt's audio or image) in use. Before removing an authoring file the media
 * module asks every source; a file any source still names is kept. Each module owns its own rule, so media never reads
 * another module's tables.
 */
public interface MediaReferenceSource {

    /** Of the given media public ids, those this module still uses. */
    Set<UUID> findReferencedMedia(Collection<UUID> mediaPublicIds);
}
