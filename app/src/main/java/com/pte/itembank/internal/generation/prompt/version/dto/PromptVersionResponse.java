package com.pte.itembank.internal.generation.prompt.version.dto;

import com.pte.itembank.internal.generation.prompt.PromptContent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One version of a prompt with everything the editor needs: the structured content to edit, the text the model would
 * receive, and the state of its approval and test. {@code lockVersion} must be sent back when saving.
 */
public record PromptVersionResponse(UUID publicId, String category, String key, int versionNo, String status,
                                    PromptContent content, String assembledText, List<String> placeholders,
                                    String authorName, UUID authorUserPublicId, String approverName,
                                    String rejectionReason, Instant createdAt, Instant submittedAt,
                                    Instant decidedAt, Instant activatedAt, long lockVersion, PromptTestInfo test) {
}
