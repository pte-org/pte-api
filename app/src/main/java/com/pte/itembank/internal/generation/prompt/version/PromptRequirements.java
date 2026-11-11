package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.prompt.PictureSettings;

import java.util.List;

/**
 * Instructions a stored prompt may not lose because code depends on them: the generator parser requires the answer
 * hint fields, and the reviewer receives the hints in a block its prompt must say how to judge.
 */
public final class PromptRequirements {

    private PromptRequirements() {
    }

    /** The phrases a prompt of this kind and key must contain; empty when it has no required instruction. */
    public static List<String> requiredPhrases(AiPromptKind kind, String promptKey) {
        if (kind == AiPromptKind.SHARED) {
            return PromptVersionConstants.SHARED_REQUIRED_PHRASES;
        }
        if (kind == AiPromptKind.REVIEWER && QuestionGenerationConstants.PROMPT_KEY_REVIEWER.equals(promptKey)) {
            return PromptVersionConstants.REVIEWER_REQUIRED_PHRASES;
        }
        if (kind == AiPromptKind.GENERATOR
                && QuestionGenerationConstants.PROMPT_KEY_DESCRIBE_IMAGE_PICTURE.equals(promptKey)) {
            return List.of(PictureSettings.MAX_ATTEMPTS, PictureSettings.WHEN_FAILS);
        }
        return List.of();
    }

    /** The required phrases missing from a prompt's assembled text. */
    public static List<String> missingPhrases(AiPromptKind kind, String promptKey, String assembledText) {
        return requiredPhrases(kind, promptKey).stream().filter(phrase -> !assembledText.contains(phrase)).toList();
    }
}
