package com.pte.itembank.internal.generation.prompt.version.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of a prompt's history. */
public record PromptVersionSummary(UUID publicId, int versionNo, String status, String authorName,
                                   String approverName, Instant createdAt, Instant submittedAt, Instant activatedAt,
                                   boolean testPassed) {
}
