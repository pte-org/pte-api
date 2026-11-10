package com.pte.scoring.rubric;

/** Who produces a trait score: the language model, Azure speech assessment, deterministic code, or an answer key. */
public enum TraitSource {
    AI_LLM,
    AZURE,
    MECHANICAL,
    OBJECTIVE
}
