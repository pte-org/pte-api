package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Checks that the audio and image prompts a question references exist, are the right kind and are usable by the author. */
@Component
public class QuestionMediaReferenceValidator {

    private static final String AUDIO_PROMPT = "AUDIO_PROMPT";
    private static final String IMAGE_PROMPT = "IMAGE_PROMPT";

    private final MediaService mediaService;

    @Autowired
    public QuestionMediaReferenceValidator(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** Skips the check when no media service is wired (focused unit tests). */
    public void validate(Question question, CurrentUser caller) {
        if (mediaService == null) {
            return;
        }
        if (question.getAudioPromptRef() != null) {
            mediaService.validateAuthoringMedia(question.getAudioPromptRef(), AUDIO_PROMPT, caller);
        }
        if (question.getImagePromptRef() != null) {
            mediaService.validateAuthoringMedia(question.getImagePromptRef(), IMAGE_PROMPT, caller);
        }
    }
}
