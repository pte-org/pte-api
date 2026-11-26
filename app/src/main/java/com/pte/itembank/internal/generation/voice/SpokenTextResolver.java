package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** What a saved question says out loud, for the task types where that is kept with the question. */
@Component
public class SpokenTextResolver {

    /**
     * The text, or empty when the voice cannot be changed: an unsupported task type, no audio, audio a person
     * recorded (no generated media), a dialogue (its turns are not kept), or no kept text.
     */
    public Optional<String> resolve(Question question) {
        if (question.getPteTaskType() == null
                || !VoiceConstants.SUPPORTED_TASK_TYPES.contains(question.getPteTaskType().name())
                || question.getAudioPromptRef() == null
                || currentVoice(question).isEmpty()) {
            return Optional.empty();
        }
        String text = question.getReferenceAnswerText();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    public Optional<String> currentVoice(Question question) {
        return GenerationMediaMeta.currentVoice(question.getGenerationMeta());
    }
}
