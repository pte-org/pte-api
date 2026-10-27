package com.pte.attempt.internal.service.capability;

import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.attempt.internal.config.CapabilityProperties;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.exception.CapabilityManifestInvalidException;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.itembank.SemanticVersion;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeObservability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Matches the immutable runtime descriptors of an exam summary or exam items
 * against a client manifest or a stored fingerprint. Pure evaluation: it never
 * looks up entitlements or content and never records audit entries.
 */
class CapabilityManifestEvaluator {

    private final CapabilityProperties properties;
    private final TaskTypeObservability observability;
    private final CapabilityManifestNormalizer normalizer;

    CapabilityManifestEvaluator(CapabilityProperties properties, TaskTypeObservability observability,
            CapabilityManifestNormalizer normalizer) {
        this.properties = properties;
        this.observability = observability;
        this.normalizer = normalizer;
    }

    CapabilityCheck evaluateSummary(List<SnapshotResponse.Item> items, ClientCapabilityManifest manifest) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtime() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtime() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            NormalizedCapabilities normalized = normalizer.normalize(manifest);
            if (manifest == null && !properties.isAllowLegacyMissingManifest()) {
                return CapabilityCheck.configuration();
            }
            return CapabilityCheck.compatible(normalized.fingerprint());
        }
        List<TaskRuntimeProfileDescriptor> profiles = items.stream().map(SnapshotResponse.Item::runtime).toList();
        return evaluateProfiles(profiles, normalizer.normalize(manifest));
    }

    CapabilityCheck evaluateItems(List<ExamItemView> items, String fingerprint) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtime() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtime() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            if (!properties.isAllowLegacyMissingManifest() && CapabilityManifestNormalizer.isBlank(fingerprint)) {
                return CapabilityCheck.configuration();
            }
            if (CapabilityManifestNormalizer.isBlank(fingerprint)) {
                return CapabilityCheck.compatible("");
            }
            try {
                return CapabilityCheck.compatible(normalizer.normalizeFingerprint(fingerprint).fingerprint());
            } catch (CapabilityManifestInvalidException ex) {
                return CapabilityCheck.configuration();
            }
        }
        try {
            NormalizedCapabilities normalized = normalizer.normalizeFingerprint(fingerprint);
            return evaluateProfiles(items.stream().map(ExamItemView::runtime).toList(), normalized);
        } catch (CapabilityManifestInvalidException ex) {
            return CapabilityCheck.configuration();
        }
    }

    CapabilityCheck evaluateItems(List<ExamItemView> items, ClientCapabilityManifest manifest) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtime() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtime() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            return CapabilityCheck.compatible(normalizer.normalize(manifest).fingerprint());
        }
        return evaluateProfiles(items.stream().map(ExamItemView::runtime).toList(), normalizer.normalize(manifest));
    }

    private CapabilityCheck evaluateProfiles(List<TaskRuntimeProfileDescriptor> profiles,
            NormalizedCapabilities provided) {
        Set<String> required = new TreeSet<>();
        List<AttemptPreflightResponse.UnsupportedTask> unsupportedTasks = new ArrayList<>();
        for (TaskRuntimeProfileDescriptor profile : profiles) {
            if (!isAllowlistedProfile(profile)) {
                unsupportedTasks.add(unsupportedTask(profile, AttemptConstants.UNSUPPORTED_RUNTIME_CONTRACT));
                continue;
            }
            if (!supportsRuntimeContract(profile, provided)) {
                unsupportedTasks.add(unsupportedTask(profile, AttemptConstants.UNSUPPORTED_RUNTIME_CONTRACT));
            }
            profile.requiredClientCapabilities().stream().map(normalizer::canonicalCapability).forEach(required::add);
        }
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(provided.values());
        if (!unsupportedTasks.isEmpty()) {
            if (observability != null) {
                observability.unsupportedRuntimePreflight();
            }
            boolean customOnly = unsupportedTasks.stream().allMatch(task ->
                    task.taskTypeKey() != null && !TaskTypeCodeCompatibility.isStandard(task.taskTypeKey()));
            if (customOnly && !properties.isStrictPreflightEnabled()) {
                return CapabilityCheck.compatible(provided.fingerprint());
            }
            return CapabilityCheck.unsupported(missing.stream().map(normalizer::displayCapability).toList(),
                    unsupportedTasks);
        }
        if (!missing.isEmpty()) {
            return CapabilityCheck.missing(missing.stream().map(normalizer::displayCapability).toList());
        }
        return CapabilityCheck.compatible(provided.fingerprint());
    }

    private boolean isAllowlistedProfile(TaskRuntimeProfileDescriptor profile) {
        try {
            if (profile == null || profile.taskTypeCode() == null || profile.screenKey() == null
                    || profile.contractVersion() < 1 || profile.answerSchemaVersion() < 1
                    || profile.scoringProfileVersion() < 1
                    || !("SCORED".equals(profile.scoringMode()) || "NONE".equals(profile.scoringMode()))) {
                return false;
            }
            if (TaskTypeCodeCompatibility.isStandard(profile.taskTypeCode())) {
                return TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
            }
            // Custom task keys are allowed to reuse a release-owned contract;
            // the key itself is not required to be present in the app enum.
            return ("ACTIVE".equals(profile.status()) || "RETIRED".equals(profile.status()))
                    && profile.behaviorKey() != null && !profile.behaviorKey().isBlank()
                    && profile.rendererKey() != null && !profile.rendererKey().isBlank()
                    && profile.scoringProfileKey() != null && !profile.scoringProfileKey().isBlank();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean supportsRuntimeContract(TaskRuntimeProfileDescriptor profile,
            NormalizedCapabilities provided) {
        if (provided.appVersion() == null && provided.supportedContracts().isEmpty()) {
            return true;
        }
        try {
            if (provided.appVersion() != null && profile.minSupportedAppVersion() != null
                    && !SemanticVersion.parse(provided.appVersion())
                            .satisfiesMinimum(profile.minSupportedAppVersion())) {
                return false;
            }
            if (!provided.supportedContracts().isEmpty()) {
                return provided.supportedContracts().stream().anyMatch(contract ->
                        Objects.equals(contract.screenKey(), profile.screenKey())
                                && Objects.equals(contract.contractVersion(), profile.contractVersion())
                                && Objects.equals(contract.answerSchemaVersion(), profile.answerSchemaVersion())
                                && Objects.equals(contract.scoringProfileVersion(), profile.scoringProfileVersion()));
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private AttemptPreflightResponse.UnsupportedTask unsupportedTask(TaskRuntimeProfileDescriptor profile,
            String reasonCode) {
        return new AttemptPreflightResponse.UnsupportedTask(
                profile == null ? null : profile.taskTypeCode(),
                profile == null ? null : profile.screenKey(),
                profile == null ? null : profile.contractVersion(),
                reasonCode,
                AttemptConstants.UNSUPPORTED_RUNTIME_CONTRACT_MESSAGE);
    }

    private boolean hasInvalidMapping(SnapshotResponse.Item item) {
        if (!hasConsistentTaskTypeMapping(item.taskType(), item.taskTypeCode(), item.section(),
                item.runtime() == null ? null : item.runtime().taskTypeCode())) {
            return true;
        }
        if (item.runtime() != null) {
            return !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL
                    .equals(item.runtimeMappingStatus())
                    || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL
                            .equals(item.runtimeMappingVersion());
        }
        return item.runtimeMappingStatus() != null || item.runtimeMappingVersion() != null
                ? !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY.equals(item.runtimeMappingStatus())
                        || !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY
                                .equals(item.runtimeMappingVersion())
                : false;
    }

    private boolean hasInvalidMapping(ExamItemView item) {
        if (!hasConsistentTaskTypeMapping(item.taskType(), item.taskTypeCode(), item.section(),
                item.runtime() == null ? null : item.runtime().taskTypeCode())) {
            return true;
        }
        if (item.runtime() != null) {
            return !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL
                    .equals(item.runtimeMappingStatus())
                    || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL
                            .equals(item.runtimeMappingVersion());
        }
        return item.runtimeMappingStatus() != null || item.runtimeMappingVersion() != null
                ? !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY.equals(item.runtimeMappingStatus())
                        || !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY
                                .equals(item.runtimeMappingVersion())
                : false;
    }

    private boolean hasConsistentTaskTypeMapping(String taskType, String taskTypeCode, String section,
            String runtimeTaskTypeCode) {
        try {
            String normalizedTaskType = TaskTypeCodeCompatibility.normalizeForLookup(
                    taskType == null ? taskTypeCode : taskType);
            String normalizedCode = taskTypeCode == null
                    ? normalizedTaskType
                    : TaskTypeCodeCompatibility.normalizeForLookup(taskTypeCode);
            if (normalizedTaskType == null || normalizedCode == null
                    || !normalizedTaskType.equals(normalizedCode)) {
                return false;
            }
            try {
                if (!TaskTypeCodeCompatibility.parse(normalizedCode).getSection().name().equals(section)) {
                    return false;
                }
            } catch (RuntimeException customKey) {
                if (section == null || section.isBlank()) {
                    return false;
                }
            }
            return runtimeTaskTypeCode == null
                    || normalizedCode.equals(TaskTypeCodeCompatibility.normalizeForLookup(runtimeTaskTypeCode));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
