package com.pte.itembank;

/** Server-derived authoring requirements for one runtime contract. */
public record TaskAuthoringRequirements(
        boolean requiresAudioPrompt,
        boolean requiresImagePrompt,
        boolean requiresPromptText,
        boolean requiresOptions,
        boolean requiresCorrectAnswer,
        boolean requiresWordCount,
        boolean requiresSingleCorrectOption,
        boolean usesOptionOrderAsCorrectPosition) {
}
