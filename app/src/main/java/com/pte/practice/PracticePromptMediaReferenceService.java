package com.pte.practice;

import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.shared.media.MediaReferenceSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Practice side of {@link MediaReferenceSource}: a practice answer pins the question's prompt files when the session
 * starts, and its review plays them back later, so they are kept while any practice answer names them.
 */
@Service
public class PracticePromptMediaReferenceService implements MediaReferenceSource {

    private final PracticeResponseRepository responses;

    public PracticePromptMediaReferenceService(PracticeResponseRepository responses) {
        this.responses = responses;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<UUID> findReferencedMedia(Collection<UUID> mediaPublicIds) {
        if (mediaPublicIds == null || mediaPublicIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> referenced = new HashSet<>(responses.findPinnedAudioPromptRefs(mediaPublicIds));
        referenced.addAll(responses.findPinnedImagePromptRefs(mediaPublicIds));
        return Set.copyOf(referenced);
    }
}
