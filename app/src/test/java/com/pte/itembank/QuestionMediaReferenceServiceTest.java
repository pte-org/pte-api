package com.pte.itembank;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionMediaReferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T04:15:00Z");

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuestionMediaReferenceService service =
            new QuestionMediaReferenceService(questions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void combinesAudioAndImageFilesKeptByQuestionsWithinTheDeletionRetention() {
        UUID audio = UUID.randomUUID();
        UUID image = UUID.randomUUID();
        UUID unused = UUID.randomUUID();
        Instant deletedSince = NOW.minus(ItembankConstants.DELETED_QUESTION_MEDIA_RETENTION);
        when(questions.findKeptAudioPromptRefs(any(), org.mockito.ArgumentMatchers.eq(deletedSince)))
                .thenReturn(List.of(audio));
        when(questions.findKeptImagePromptRefs(any(), org.mockito.ArgumentMatchers.eq(deletedSince)))
                .thenReturn(List.of(image));

        assertThat(service.findReferencedMedia(List.of(audio, image, unused))).containsExactlyInAnyOrder(audio, image);
    }

    @Test
    void noIdsMeansNoQuery() {
        assertThat(service.findReferencedMedia(List.of())).isEmpty();
        verifyNoInteractions(questions);
    }
}
