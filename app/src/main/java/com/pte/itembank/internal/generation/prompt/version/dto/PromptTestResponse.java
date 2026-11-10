package com.pte.itembank.internal.generation.prompt.version.dto;

import java.time.Instant;

/** What a test run found: whether the model's answer to this prompt was usable, why not, and what it wrote. */
public record PromptTestResponse(boolean passed, String note, String sampleOutput, Instant testedAt) {
}
