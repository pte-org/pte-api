package com.pte.itembank;

import com.pte.itembank.domain.TaskRuntimeContract;
import com.pte.itembank.internal.constant.TaskRuntimeProfileConstants;
import com.pte.itembank.internal.exception.TaskRuntimeProfileException;
import com.pte.itembank.internal.repository.TaskRuntimeContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only public resolver for release-owned screen contracts. Consumers use
 * semantic keys and never depend on the JPA registry implementation.
 */
@Service
public class TaskRuntimeContractService {

    private final TaskRuntimeContractRepository repository;

    @Autowired
    public TaskRuntimeContractService(TaskRuntimeContractRepository repository) {
        this.repository = repository;
    }

    /** Compatibility constructor for pure resolver tests before persistence. */
    public TaskRuntimeContractService() {
        this.repository = null;
    }

    @Transactional(readOnly = true)
    public TaskRuntimeContractDescriptor resolveActive(String screenKey, int contractVersion) {
        TaskRuntimeContractDescriptor descriptor = resolveReadable(screenKey, contractVersion);
        if (!descriptor.active()) {
            throw TaskRuntimeProfileException.notActive();
        }
        return descriptor;
    }

    /**
     * Resolves a persisted contract for catalog/history reads. A retired
     * contract is intentionally readable here so an existing task type can
     * still display its binding and explain why it is unavailable for new
     * authoring. New task types and template activation must use
     * {@link #resolveActive(String, int)} instead.
     */
    @Transactional(readOnly = true)
    public TaskRuntimeContractDescriptor resolveReadable(String screenKey, int contractVersion) {
        if (repository == null) {
            return staticContract(screenKey, contractVersion, true);
        }
        TaskRuntimeContract contract = repository
                .findByScreenKeyAndContractVersionAndDeletedFalse(screenKey, contractVersion)
                .orElseThrow(TaskRuntimeProfileException::notFound);
        TaskRuntimeContractDescriptor descriptor = toDescriptor(contract);
        validateDescriptor(descriptor);
        return descriptor;
    }

    /** Resolve all contracts with one repository call; missing entries remain visible to the caller. */
    @Transactional(readOnly = true)
    public Map<TaskRuntimeContractReference, TaskRuntimeContractDescriptor> resolveBatch(
            Collection<TaskRuntimeContractReference> references, boolean allowRetired) {
        Map<TaskRuntimeContractReference, TaskRuntimeContractDescriptor> resolved = new LinkedHashMap<>();
        if (references == null || references.isEmpty()) {
            return resolved;
        }
        references.forEach(reference -> resolved.put(reference, null));
        if (repository == null) {
            resolved.replaceAll((reference, ignored) -> staticContract(
                    reference.screenKey(), reference.contractVersion(), allowRetired));
            return resolved;
        }
        List<String> screens = references.stream().map(TaskRuntimeContractReference::screenKey).distinct().toList();
        List<Integer> versions = references.stream().map(TaskRuntimeContractReference::contractVersion).distinct().toList();
        repository.findAllByScreenKeyInAndContractVersionInAndDeletedFalse(screens, versions).forEach(contract -> {
            TaskRuntimeContractDescriptor descriptor = toDescriptor(contract);
            if ((descriptor.active() || (allowRetired && "RETIRED".equals(descriptor.status())))) {
                validateDescriptor(descriptor);
                resolved.put(new TaskRuntimeContractReference(descriptor.screenKey(), descriptor.contractVersion()), descriptor);
            }
        });
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<TaskRuntimeContractDescriptor> listSelectable() {
        if (repository == null) {
            return TaskRuntimeProfileRegistry.all().stream()
                    .map(profile -> staticContract(profile.rendererKey(), profile.profileVersion(), true))
                    .toList();
        }
        return repository.findAllByStatusAndDeletedFalseOrderByScreenKeyAscContractVersionAsc("ACTIVE")
                .stream().map(this::toDescriptor).peek(this::validateDescriptor).toList();
    }

    public TaskRuntimeContractDescriptor fromLegacyProfile(String taskTypeKey) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor(taskTypeKey);
        return staticContract(profile.rendererKey(), profile.profileVersion(), true);
    }

    private TaskRuntimeContractDescriptor toDescriptor(TaskRuntimeContract contract) {
        return new TaskRuntimeContractDescriptor(
                contract.getScreenKey(), contract.getContractVersion(), contract.getProfileKey(),
                contract.getProfileVersion(), contract.getBehaviorKey(), contract.getRendererKey(),
                contract.getAnswerSchemaVersion(), contract.getScoringProfileKey(),
                contract.getScoringProfileVersion(), contract.getScoringMode(),
                contract.getRequiredClientCapabilities() == null
                        ? List.of() : List.of(contract.getRequiredClientCapabilities()),
                contract.getMinSupportedAppVersion(), contract.getAuthoringContractKey(),
                contract.getAuthoringContractVersion(),
                new TaskAuthoringRequirements(contract.isRequiresAudioPrompt(), contract.isRequiresImagePrompt(),
                        contract.isRequiresPromptText(), contract.isRequiresOptions(),
                        contract.isRequiresCorrectAnswer(), contract.isRequiresWordCount(),
                        contract.isRequiresSingleCorrectOption(),
                        contract.isUsesOptionOrderAsCorrectPosition()),
                contract.getStatus());
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
        TaskAuthoringRequirements requirements = new TaskAuthoringRequirements(
                false, false, false, false, false, false, false, false);
        return new TaskRuntimeContractDescriptor(
                profile.rendererKey(), contractVersion, profile.profileKey(), profile.profileVersion(),
                profile.behaviorKey(), profile.rendererKey(), profile.answerSchemaVersion(),
                profile.scoringProfileKey(), profile.scoringProfileVersion(),
                "UNSCORED".equals(profile.scoringProfileKey()) ? "NONE" : "SCORED",
                profile.requiredClientCapabilities(), "1.0.0", "PTE." + profile.taskTypeCode() + "_AUTHORING",
                1, requirements, profile.status());
    }

    private void validateDescriptor(TaskRuntimeContractDescriptor descriptor) {
        if (descriptor.screenKey() == null || descriptor.screenKey().isBlank()
                || descriptor.contractVersion() < 1 || descriptor.profileVersion() < 1
                || descriptor.answerSchemaVersion() < 1 || descriptor.scoringProfileVersion() < 1
                || descriptor.authoringContractVersion() < 1
                || (!"SCORED".equals(descriptor.scoringMode()) && !"NONE".equals(descriptor.scoringMode()))) {
            throw new TaskRuntimeProfileException(TaskRuntimeProfileConstants.PROFILE_NOT_ALLOWED);
        }
        if (descriptor.minSupportedAppVersion() != null) {
            SemanticVersion.parse(descriptor.minSupportedAppVersion());
        }
        if (descriptor.requiredClientCapabilities().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new TaskRuntimeProfileException(TaskRuntimeProfileConstants.PROFILE_NOT_ALLOWED);
        }
    }
}
