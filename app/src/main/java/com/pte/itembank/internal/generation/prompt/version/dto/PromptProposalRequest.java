package com.pte.itembank.internal.generation.prompt.version.dto;

import com.pte.itembank.internal.generation.prompt.PromptContent;
import jakarta.validation.constraints.NotNull;

/**
 * The content someone proposes for a prompt. It starts the prompt's proposal when none is open, or replaces the
 * content of the open one; then {@code expectedLockVersion} is the one the editor loaded, so a lost update is refused.
 */
public record PromptProposalRequest(@NotNull PromptContent content, Long expectedLockVersion) {
}
