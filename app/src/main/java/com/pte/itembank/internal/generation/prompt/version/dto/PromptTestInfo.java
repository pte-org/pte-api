package com.pte.itembank.internal.generation.prompt.version.dto;

import java.time.Instant;

/**
 * The last test of a version. {@code current} is false when the content was edited after the test ran, so the result
 * no longer counts for sending the version for approval.
 */
public record PromptTestInfo(boolean passed, boolean current, String note, Instant testedAt, String testedByName) {
}
