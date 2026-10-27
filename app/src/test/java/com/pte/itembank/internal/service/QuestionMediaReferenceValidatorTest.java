package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class QuestionMediaReferenceValidatorTest {

    private final MediaService mediaService = mock(MediaService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @Test
    void anAudioAndAnImageReferenceAreEachCheckedAgainstTheirOwnAssetKind() {
        Question question = new Question();
        UUID audio = UUID.randomUUID();
        UUID image = UUID.randomUUID();
        question.setAudioPromptRef(audio);
        question.setImagePromptRef(image);

        new QuestionMediaReferenceValidator(mediaService).validate(question, caller);

        verify(mediaService).validateAuthoringMedia(audio, "AUDIO_PROMPT", caller);
        verify(mediaService).validateAuthoringMedia(image, "IMAGE_PROMPT", caller);
    }

    @Test
    void aQuestionWithoutMediaChecksNothing() {
        new QuestionMediaReferenceValidator(mediaService).validate(new Question(), caller);

        verify(mediaService, never()).validateAuthoringMedia(any(), anyString(), any());
    }

    @Test
    void withoutAMediaServiceTheCheckIsSkipped() {
        Question question = new Question();
        question.setAudioPromptRef(UUID.randomUUID());

        new QuestionMediaReferenceValidator(null).validate(question, caller);
    }
}
