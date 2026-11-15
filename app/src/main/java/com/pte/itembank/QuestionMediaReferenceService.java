package com.pte.itembank;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.media.MediaReferenceSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Question bank side of {@link MediaReferenceSource}: a prompt file is used by every question that is not deleted, and
 * by a deleted one for {@link ItembankConstants#DELETED_QUESTION_MEDIA_RETENTION} after its deletion, so a question
 * deleted by mistake can still come back with its audio and image.
 */
@Service
public class QuestionMediaReferenceService implements MediaReferenceSource {

    private final QuestionRepository questions;
    private final Clock clock;

    public QuestionMediaReferenceService(QuestionRepository questions, Clock clock) {
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<UUID> findReferencedMedia(Collection<UUID> mediaPublicIds) {
        if (mediaPublicIds == null || mediaPublicIds.isEmpty()) {
            return Set.of();
        }
        Instant deletedSince = clock.instant().minus(ItembankConstants.DELETED_QUESTION_MEDIA_RETENTION);
        Set<UUID> referenced = new HashSet<>(questions.findKeptAudioPromptRefs(mediaPublicIds, deletedSince));
        referenced.addAll(questions.findKeptImagePromptRefs(mediaPublicIds, deletedSince));
        return Set.copyOf(referenced);
    }
}
