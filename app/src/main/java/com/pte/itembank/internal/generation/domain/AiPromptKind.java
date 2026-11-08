package com.pte.itembank.internal.generation.domain;

/** What a prompt is for; with the prompt key it names one editable prompt. */
public enum AiPromptKind {
    /** The instructions for writing one task type. */
    GENERATOR,
    /** The reviewer's instructions. */
    REVIEWER,
    /** The instructions the AI scorer receives for one task type. */
    SCORER,
    /** A block shared by several prompts. */
    SHARED,
    /** The study chatbot's instructions and its fixed replies. */
    CHATBOT
}
