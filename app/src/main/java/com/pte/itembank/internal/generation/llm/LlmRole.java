package com.pte.itembank.internal.generation.llm;

/** The two agents of the pipeline; each has its own model and temperature in the configuration. */
public enum LlmRole {
    GENERATOR, REVIEWER
}
