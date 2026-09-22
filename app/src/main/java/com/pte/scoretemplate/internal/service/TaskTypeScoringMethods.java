package com.pte.scoretemplate.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;

import java.util.Map;

/**
 * Resolves the template module's legacy enum from the allowlisted runtime
 * profile. The executable choice is keyed by the stable profile key, not by
 * a second task-type map, so adding a task that uses an existing scoring
 * strategy cannot silently drift from the canonical itembank registry.
 */
final class TaskTypeScoringMethods {

    private static final Map<String, ScoringMethod> BY_PROFILE_KEY = Map.of(
            "AI_SPEECH", ScoringMethod.AI_SPEECH,
            "AI_TEXT", ScoringMethod.AI_TEXT,
            "OBJECTIVE", ScoringMethod.OBJECTIVE,
            "UNSCORED", ScoringMethod.UNSCORED);

    private TaskTypeScoringMethods() {
    }

    static ScoringMethod resolve(String taskType) {
        final TaskRuntimeProfileDescriptor profile;
        try {
            String canonicalTaskType = TaskTypeCodeCompatibility.canonicalize(taskType);
            profile = TaskRuntimeProfileRegistry.descriptorFor(canonicalTaskType);
        } catch (RuntimeException ex) {
            throw unknownTaskType(taskType);
        }
        return resolve(profile);
    }

    static ScoringMethod resolve(TaskRuntimeProfileDescriptor profile) {
        if (profile == null || profile.profileKey() == null || profile.scoringProfileKey() == null
                || profile.scoringProfileVersion() < 1
                || !("ACTIVE".equals(profile.status()) || "RETIRED".equals(profile.status()))) {
            throw new ScoreTemplateValidationException(
                    "Runtime profile is not allowlisted for scoring: "
                            + (profile == null ? "unknown" : profile.taskTypeCode()));
        }
        if (TaskTypeCodeCompatibility.isStandard(profile.taskTypeCode())
                && !TaskRuntimeProfileRegistry.isAllowlistedContract(profile)) {
            throw new ScoreTemplateValidationException(
                    "Runtime profile is not allowlisted for scoring: " + profile.taskTypeCode());
        }
        ScoringMethod method = BY_PROFILE_KEY.get(profile.scoringProfileKey());
        if (method == null) {
            throw new ScoreTemplateValidationException(
                    "Runtime profile has no registered scoring strategy: " + profile.scoringProfileKey());
        }
        return method;
    }

    private static ScoreTemplateValidationException unknownTaskType(String taskType) {
        return new ScoreTemplateValidationException(
                "Unknown task type '" + taskType + "' has no allowlisted runtime scoring profile");
    }
}
