package com.pte.itembank;

import com.pte.itembank.internal.constant.TaskRuntimeProfileConstants;
import com.pte.itembank.internal.exception.TaskRuntimeProfileException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only public resolver for release-owned screen contracts. Consumers use semantic keys; the contracts are
 * derived from the code-owned {@link TaskRuntimeProfileRegistry}, so a retired contract stays readable for history.
 */
@Service
public class TaskRuntimeContractService {

    public TaskRuntimeContractDescriptor resolveActive(String screenKey, int contractVersion) {
        return staticContract(screenKey, contractVersion, false);
    }

    /**
     * Resolves a contract for catalog/history reads. A retired contract is intentionally readable here so an existing
     * task type can still display its binding; new authoring must use {@link #resolveActive(String, int)}.
     */
    public TaskRuntimeContractDescriptor resolveReadable(String screenKey, int contractVersion) {
        return staticContract(screenKey, contractVersion, true);
    }

    public List<TaskRuntimeContractDescriptor> listSelectable() {
        return TaskRuntimeProfileRegistry.all().stream()
                .map(profile -> staticContract(profile.rendererKey(), profile.profileVersion(), true))
                .toList();
    }

    public TaskRuntimeContractDescriptor fromLegacyProfile(String taskTypeKey) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor(taskTypeKey);
        return staticContract(profile.rendererKey(), profile.profileVersion(), true);
    }

    private TaskRuntimeContractDescriptor staticContract(String screenKey, int contractVersion,
            boolean allowRetired) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.all().stream()
                .filter(candidate -> candidate.rendererKey().equals(screenKey)
                        && candidate.profileVersion() == contractVersion)
                .findFirst().orElseThrow(TaskRuntimeProfileException::notFound);
        if (!allowRetired && !profile.active()) {
            throw TaskRuntimeProfileException.notActive();
        }
        TaskAuthoringRequirements requirements = TaskRuntimeProfileRegistry
                .authoringRequirementsFor(profile.taskTypeCode());
        return new TaskRuntimeContractDescriptor(
                profile.rendererKey(), contractVersion, profile.profileKey(), profile.profileVersion(),
                profile.behaviorKey(), profile.rendererKey(), profile.answerSchemaVersion(),
                profile.scoringProfileKey(), profile.scoringProfileVersion(),
                "UNSCORED".equals(profile.scoringProfileKey()) ? "NONE" : "SCORED",
                profile.requiredClientCapabilities(), "1.0.0", "PTE." + profile.taskTypeCode() + "_AUTHORING",
                1, requirements, profile.status());
    }
}
