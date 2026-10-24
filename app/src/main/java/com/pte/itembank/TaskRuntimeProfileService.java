package com.pte.itembank;

import com.pte.itembank.internal.exception.TaskRuntimeProfileException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Public itembank facade for resolving the allowlisted runtime profile of a task type. The code-owned
 * {@link TaskRuntimeProfileRegistry} is the only source: no database value can introduce a renderer or scorer.
 */
@Service
public class TaskRuntimeProfileService {

    public TaskRuntimeProfileDescriptor resolveActive(String taskTypeCode) {
        return TaskRuntimeProfileRegistry.descriptorFor(TaskTypeCodeCompatibility.canonicalize(taskTypeCode));
    }

    /** Resolves all requested task types, keyed by canonical code. */
    public Map<String, TaskRuntimeProfileDescriptor> resolveActiveByTaskTypeCodes(Collection<String> taskTypeCodes) {
        Map<String, TaskRuntimeProfileDescriptor> resolved = new LinkedHashMap<>();
        for (String taskTypeCode : taskTypeCodes) {
            String canonicalCode = TaskTypeCodeCompatibility.canonicalize(taskTypeCode);
            resolved.put(canonicalCode, TaskRuntimeProfileRegistry.descriptorFor(canonicalCode));
        }
        return resolved;
    }

    /** Returns the pinned profiles that are not an exact allowlisted contract. */
    public Set<TaskRuntimeProfileDescriptor> invalidPinnedProfiles(
            Collection<TaskRuntimeProfileDescriptor> pinnedProfiles) {
        Set<TaskRuntimeProfileDescriptor> invalid = new LinkedHashSet<>();
        if (pinnedProfiles == null) {
            return invalid;
        }
        for (TaskRuntimeProfileDescriptor pinned : pinnedProfiles) {
            try {
                if (!TaskRuntimeProfileRegistry.isAllowlistedContract(pinned)) {
                    invalid.add(pinned);
                }
            } catch (RuntimeException ex) {
                invalid.add(pinned);
            }
        }
        return invalid;
    }
}
