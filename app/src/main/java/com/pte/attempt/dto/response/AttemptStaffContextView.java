package com.pte.attempt.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.Map;
import java.util.UUID;

/** Canonical identity and exam task counts, without prompts or answer keys. */
public record AttemptStaffContextView(UUID attemptPublicId, UUID sessionPublicId,
        UUID examDefinitionPublicId, boolean examContextValid, Map<String, Long> taskCounts,
        Map<String, TaskRuntimeProfileDescriptor> runtimes) {
    public AttemptStaffContextView { taskCounts = Map.copyOf(taskCounts); runtimes = Map.copyOf(runtimes); }
}
