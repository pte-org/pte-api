package com.pte.itembank;

import com.pte.itembank.domain.TaskRuntimeProfile;
import com.pte.itembank.domain.enums.TaskRuntimeProfileStatus;
import com.pte.itembank.internal.exception.TaskRuntimeProfileException;
import com.pte.itembank.internal.repository.TaskRuntimeProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public itembank facade for resolving immutable, allowlisted runtime profiles. */
@Service
public class TaskRuntimeProfileService {

    private final TaskRuntimeProfileRepository repository;

    @Autowired
    public TaskRuntimeProfileService(TaskRuntimeProfileRepository repository) {
        this.repository = repository;
    }

    /** Compatibility constructor for focused tests that use the static allowlist. */
    public TaskRuntimeProfileService() {
        this.repository = null;
    }

    @Transactional(readOnly = true)
    public TaskRuntimeProfileDescriptor resolveActive(String taskTypeCode) {
        String canonicalCode = TaskTypeCodeCompatibility.canonicalize(taskTypeCode);
        if (repository == null) {
            return TaskRuntimeProfileRegistry.descriptorFor(canonicalCode);
        }
        TaskRuntimeProfile profile = repository
                .findByTaskTypeCodeAndStatusAndDeletedFalse(canonicalCode, TaskRuntimeProfileStatus.ACTIVE)
                .orElseThrow(TaskRuntimeProfileException::notFound);
        return validateAndMap(profile, true);
    }

    /** Resolves all requested task types with one repository query. */
    @Transactional(readOnly = true)
    public Map<String, TaskRuntimeProfileDescriptor> resolveActiveByTaskTypeCodes(
            Collection<String> taskTypeCodes) {
        Map<String, TaskRuntimeProfileDescriptor> resolved = new LinkedHashMap<>();
        for (String taskTypeCode : taskTypeCodes) {
            String canonicalCode = TaskTypeCodeCompatibility.canonicalize(taskTypeCode);
            resolved.put(canonicalCode, null);
        }
        if (repository == null) {
            resolved.replaceAll((code, ignored) -> TaskRuntimeProfileRegistry.descriptorFor(code));
            return resolved;
        }
        List<TaskRuntimeProfile> profiles = repository.findAllByTaskTypeCodeInAndStatusAndDeletedFalse(
                resolved.keySet(), TaskRuntimeProfileStatus.ACTIVE);
        for (TaskRuntimeProfile profile : profiles) {
            resolved.put(profile.getTaskTypeCode(), validateAndMap(profile, true));
        }
        if (resolved.values().stream().anyMatch(value -> value == null)) {
            throw TaskRuntimeProfileException.notFound();
        }
        return resolved;
    }

    /** Reads a pinned version; retired profiles remain readable for history. */
    @Transactional(readOnly = true)
    public TaskRuntimeProfileDescriptor resolvePinned(String taskTypeCode, Integer profileVersion) {
        String canonicalCode = TaskTypeCodeCompatibility.canonicalize(taskTypeCode);
        if (profileVersion == null || repository == null) {
            return resolveActive(canonicalCode);
        }
        TaskRuntimeProfile profile = repository
                .findByTaskTypeCodeAndProfileVersionAndDeletedFalse(canonicalCode, profileVersion)
                .orElseThrow(TaskRuntimeProfileException::notFound);
        return validateAndMap(profile, false);
    }

    /** Checks that a template item keeps an allowlisted profile version. */
    public void validatePinned(TaskRuntimeProfileDescriptor pinned) {
        if (!invalidPinnedProfiles(List.of(pinned)).isEmpty()) {
            throw TaskRuntimeProfileException.notAllowed();
        }
    }

    /**
     * Validates all pinned contracts with one persistence lookup. This keeps
     * template/readiness validation from turning the 22 template rows into an
     * N+1 profile query pattern.
     */
    @Transactional(readOnly = true)
    public Set<TaskRuntimeProfileDescriptor> invalidPinnedProfiles(
            Collection<TaskRuntimeProfileDescriptor> pinnedProfiles) {
        Set<TaskRuntimeProfileDescriptor> invalid = new LinkedHashSet<>();
        if (pinnedProfiles == null || pinnedProfiles.isEmpty()) {
            return invalid;
        }

        Map<String, TaskRuntimeProfileDescriptor> expected = new LinkedHashMap<>();
        for (TaskRuntimeProfileDescriptor pinned : pinnedProfiles) {
            try {
                if (!TaskRuntimeProfileRegistry.isAllowlistedContract(pinned)) {
                    invalid.add(pinned);
                } else {
                    expected.put(profileKey(pinned), pinned);
                }
            } catch (RuntimeException ex) {
                invalid.add(pinned);
            }
        }

        if (repository == null || expected.isEmpty()) {
            return invalid;
        }

        Set<String> taskTypeCodes = new LinkedHashSet<>();
        Set<Integer> profileVersions = new LinkedHashSet<>();
        expected.values().forEach(profile -> {
            taskTypeCodes.add(profile.taskTypeCode());
            profileVersions.add(profile.profileVersion());
        });
        Map<String, TaskRuntimeProfileDescriptor> persisted = new LinkedHashMap<>();
        repository.findAllByTaskTypeCodeInAndProfileVersionInAndDeletedFalse(taskTypeCodes, profileVersions)
                .forEach(profile -> persisted.put(profileKey(profile.getTaskTypeCode(), profile.getProfileVersion()),
                        toDescriptor(profile)));

        expected.forEach((key, requested) -> {
            TaskRuntimeProfileDescriptor actual = persisted.get(key);
            if (actual == null || !TaskRuntimeProfileRegistry.isAllowlistedContract(actual)
                    || !sameImmutableContract(actual, requested)
                    || !actual.status().equals(requested.status())) {
                invalid.add(requested);
            }
        });
        return invalid;
    }

    private TaskRuntimeProfileDescriptor validateAndMap(TaskRuntimeProfile profile, boolean requireActive) {
        TaskRuntimeProfileDescriptor actual = toDescriptor(profile);
        if (!TaskRuntimeProfileRegistry.isAllowlistedContract(actual)) {
            throw TaskRuntimeProfileException.notAllowed();
        }
        if (requireActive && !actual.active()) {
            throw TaskRuntimeProfileException.notActive();
        }
        return actual;
    }

    private TaskRuntimeProfileDescriptor toDescriptor(TaskRuntimeProfile profile) {
        List<String> capabilities = profile.getRequiredClientCapabilities() == null
                || profile.getRequiredClientCapabilities().isBlank()
                ? List.of()
                : List.of(profile.getRequiredClientCapabilities().split(","));
        return new TaskRuntimeProfileDescriptor(
                profile.getTaskTypeCode(), profile.getProfileKey(), profile.getProfileVersion(),
                profile.getBehaviorKey(), profile.getRendererKey(), profile.getAnswerSchemaVersion(),
                profile.getScoringProfileKey(), profile.getScoringProfileVersion(), capabilities,
                profile.getStatus().name());
    }

    private String profileKey(TaskRuntimeProfileDescriptor profile) {
        return profileKey(profile.taskTypeCode(), profile.profileVersion());
    }

    private String profileKey(String taskTypeCode, int profileVersion) {
        return taskTypeCode + "#" + profileVersion;
    }

    private boolean sameImmutableContract(TaskRuntimeProfileDescriptor expected,
            TaskRuntimeProfileDescriptor actual) {
        return expected.taskTypeCode().equals(actual.taskTypeCode())
                && expected.profileKey().equals(actual.profileKey())
                && expected.profileVersion() == actual.profileVersion()
                && expected.behaviorKey().equals(actual.behaviorKey())
                && expected.rendererKey().equals(actual.rendererKey())
                && expected.answerSchemaVersion() == actual.answerSchemaVersion()
                && expected.scoringProfileKey().equals(actual.scoringProfileKey())
                && expected.scoringProfileVersion() == actual.scoringProfileVersion()
                && expected.requiredClientCapabilities().equals(actual.requiredClientCapabilities());
    }
}
