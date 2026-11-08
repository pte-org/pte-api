package com.pte.itembank.internal.generation.prompt.version.dto;

import java.util.List;

/**
 * Everything the prompt page shows: the version in use, the open proposal for changing it (null when there is none)
 * and the changes accepted so far, newest first.
 */
public record PromptWorkspaceResponse(PromptVersionResponse live, PromptVersionResponse proposal,
                                      List<PromptVersionSummary> history) {
}
