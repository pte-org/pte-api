package com.pte.attempt.dto.response;

import java.util.Map;
import java.util.UUID;

/** Canonical identity and pinned task counts, without prompts or answer keys. */
public record AttemptStaffContextView(UUID attemptPublicId, UUID sessionPublicId,
        UUID scoreTemplatePublicId, boolean pinnedContextValid, Map<String, Long> taskCounts) {
    public AttemptStaffContextView { taskCounts = Map.copyOf(taskCounts); }
}
