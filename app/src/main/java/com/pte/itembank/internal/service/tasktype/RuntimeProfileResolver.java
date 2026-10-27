package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeProfileService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves runtime profile metadata for task types from either the profile
 * service or the fallback registry.
 */
@Component
public class RuntimeProfileResolver {

    private final TaskRuntimeProfileService profileService;

    public RuntimeProfileResolver(TaskRuntimeProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Resolve profiles for a list of definitions, using profile service if
     * available or registry fallback.
     */
    public Map<String, TaskRuntimeProfileDescriptor> resolveProfileMap(List<QuestionType> definitions) {
        if (definitions.isEmpty()) {
            return Map.of();
        }
        if (profileService == null) {
            Map<String, TaskRuntimeProfileDescriptor> fallback = new LinkedHashMap<>();
            definitions.forEach(definition -> fallback.put(definition.getCode(),
                    TaskRuntimeProfileRegistry.descriptorFor(definition.getCode())));
            return fallback;
        }
        return profileService.resolveActiveByTaskTypeCodes(
                definitions.stream().map(QuestionType::getCode).toList());
    }

    /**
     * Resolve a single profile by code.
     */
    public TaskRuntimeProfileDescriptor resolveProfile(String code) {
        return profileService == null
                ? TaskRuntimeProfileRegistry.descriptorFor(code)
                : profileService.resolveActive(code);
    }
}
