package com.pte.assessment;

import com.pte.assessment.internal.repository.ExamVersionRepository;
import com.pte.shared.media.MediaReferenceSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Assessment side of {@link MediaReferenceSource}: a published exam version freezes its questions' prompt files, and
 * the attempts and results of that version rely on them, so they are kept for as long as the version exists.
 */
@Service
public class ExamVersionMediaReferenceService implements MediaReferenceSource {

    private final ExamVersionRepository versions;

    public ExamVersionMediaReferenceService(ExamVersionRepository versions) {
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<UUID> findReferencedMedia(Collection<UUID> mediaPublicIds) {
        if (mediaPublicIds == null || mediaPublicIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> referenced = new HashSet<>(versions.findFrozenAudioPromptRefs(mediaPublicIds));
        referenced.addAll(versions.findFrozenImagePromptRefs(mediaPublicIds));
        return Set.copyOf(referenced);
    }
}
