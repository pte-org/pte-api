package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.InvalidScoringProfileException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces the old hardcoded AI/objective task-type catalogs: {@code
 * scoringMethod} per task type comes from the {@code ScoreTemplate} pinned
 * to the answer's attempt (spec FR-07), resolved through {@code
 * ScoreTemplateService} rather than two parallel hardcoded sets.
 *
 * <p>Cached per {@code scoreTemplatePublicId} for the JVM's lifetime — safe
 * because a {@code ScoreTemplate}, once pinned to any snapshot, is
 * immutable (ACTIVE/RETIRED never change, per {@code
 * ScoreTemplateAdminService}'s write-once convention). Avoids re-fetching
 * the same template once per answer in a session's scoring loop.
 */
@Service
public class ScoringMethodResolver {

    private final ScoreTemplateService scoreTemplateService;
    private final Map<UUID, Map<String, ResolvedTaskProfile>> cacheByTemplate = new ConcurrentHashMap<>();

    public ScoringMethodResolver(ScoreTemplateService scoreTemplateService) {
        this.scoreTemplateService = scoreTemplateService;
    }

    /** Empty when {@code taskType} has no row in this template (e.g. PERSONAL_INTRODUCTION) — callers treat that the same as UNSCORED: leave the answer PENDING. */
    public Optional<ScoringMethod> resolve(UUID scoreTemplatePublicId, String taskType) {
        return findResolvedProfile(scoreTemplatePublicId, taskType).map(ResolvedTaskProfile::method);
    }

    public Optional<String> resolveSection(UUID scoreTemplatePublicId, String taskType) {
        return findResolvedProfile(scoreTemplatePublicId, taskType).map(ResolvedTaskProfile::section);
    }

    Optional<TaskRuntimeProfileDescriptor> resolveProfile(UUID scoreTemplatePublicId, String taskType) {
        return findResolvedProfile(scoreTemplatePublicId, taskType).map(ResolvedTaskProfile::runtime);
    }

    private Optional<ResolvedTaskProfile> findResolvedProfile(UUID scoreTemplatePublicId, String taskType) {
        String key = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskType);
        return Optional.ofNullable(cacheByTemplate.computeIfAbsent(scoreTemplatePublicId, this::loadTemplate)
                .get(key));
    }

    private Map<String, ResolvedTaskProfile> loadTemplate(UUID scoreTemplatePublicId) {
        Map<String, ResolvedTaskProfile> resolved = new LinkedHashMap<>();
        for (ScoreTemplateItemResponse item : scoreTemplateService.getByPublicId(scoreTemplatePublicId).items()) {
            String taskType = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                    item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
            resolved.put(taskType, resolveItem(taskType, item));
        }
        return Map.copyOf(resolved);
    }

    private ResolvedTaskProfile resolveItem(String canonicalTaskType, ScoreTemplateItemResponse item) {
        TaskRuntimeProfileDescriptor runtime = item.runtime();
        if (runtime == null) {
            // Compatibility adapter for templates created before the runtime
            // contract was added. New templates always carry a profile and
            // therefore take the strict branch below.
            ScoringMethod method = parseLegacyScoringMethod(canonicalTaskType, item.scoringMethod());
            TaskRuntimeProfileDescriptor legacyRuntime = TaskTypeCodeCompatibility.isStandard(canonicalTaskType)
                    ? TaskRuntimeProfileRegistry.descriptorFor(canonicalTaskType) : null;
            return new ResolvedTaskProfile(legacyRuntime, method, item.section());
        }
        if (!canonicalTaskType.equals(TaskTypeCodeCompatibility.normalizeTaskTypeKey(runtime.taskTypeCode()))) {
            throw invalidProfile(canonicalTaskType, ScoringConstants.RUNTIME_PROFILE_TASK_TYPE_MISMATCH);
        }
        ScoringMethod method = ScoringProfileRegistry.resolve(runtime)
                .orElseThrow(() -> invalidProfile(canonicalTaskType,
                        ScoringConstants.EXECUTABLE_SCORING_STRATEGY_MISSING));
        ScoringMethod persistedMethod = parseLegacyScoringMethod(canonicalTaskType, item.scoringMethod());
        if (persistedMethod != method) {
            throw invalidProfile(canonicalTaskType, ScoringConstants.TEMPLATE_SCORING_METHOD_MISMATCH);
        }
        return new ResolvedTaskProfile(runtime, method, item.section());
    }

    private ScoringMethod parseLegacyScoringMethod(String taskType, String rawMethod) {
        try {
            return ScoringMethod.valueOf(rawMethod);
        } catch (RuntimeException ex) {
            throw invalidProfile(taskType, ScoringConstants.TEMPLATE_SCORING_METHOD_UNKNOWN);
        }
    }

    private InvalidScoringProfileException invalidProfile(String taskType, String reason) {
        return new InvalidScoringProfileException(taskType, reason);
    }

    private record ResolvedTaskProfile(TaskRuntimeProfileDescriptor runtime, ScoringMethod method, String section) {
    }
}
