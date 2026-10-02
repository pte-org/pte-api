package com.pte.itembank;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypeAvailabilityResponse;
import com.pte.itembank.dto.response.TaskTypeCapabilityResponse;
import com.pte.itembank.dto.response.TaskTypePageResponse;
import com.pte.itembank.internal.exception.InvalidQuestionTypeException;
import com.pte.itembank.internal.exception.QuestionTypeCodeAlreadyUsedException;
import com.pte.itembank.internal.exception.QuestionTypeNotFoundException;
import com.pte.itembank.internal.exception.TaskTypeCapabilityNotFoundException;
import com.pte.itembank.internal.exception.TaskTypeDisplayNameAlreadyUsedException;
import com.pte.itembank.internal.exception.TaskTypeDisplayNameInvalidException;
import com.pte.itembank.internal.exception.TaskTypeKeyAlreadyUsedException;
import com.pte.itembank.internal.exception.TaskTypeKeyInvalidException;
import com.pte.itembank.internal.exception.TaskTypeRuntimeLockedException;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Source of truth for the question-type catalog used by authoring clients and
 * task-specific validation.
 */
@Service
public class QuestionTypeService {

    private final QuestionTypeRepository repository;
    private final TaskRuntimeProfileService runtimeProfileService;
    private final TaskRuntimeContractService runtimeContractService;
    private final TaskTypePublicationUsageService publicationUsageService;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;

    @Autowired
    public QuestionTypeService(QuestionTypeRepository repository, TaskRuntimeProfileService runtimeProfileService,
            TaskRuntimeContractService runtimeContractService,
            TaskTypePublicationUsageService publicationUsageService,
            AuditLogService auditLogService, TaskTypeObservability observability) {
        this.repository = repository;
        this.runtimeProfileService = runtimeProfileService;
        this.runtimeContractService = runtimeContractService;
        this.publicationUsageService = publicationUsageService;
        this.auditLogService = auditLogService;
        this.observability = observability;
    }

    /** Compatibility constructor for focused catalog tests. */
    public QuestionTypeService(QuestionTypeRepository repository) {
        this(repository, null, new TaskRuntimeContractService(), null, null, null);
    }

    @Transactional(readOnly = true)
    public List<QuestionTypeResponse> list(boolean activeOnly) {
        markLegacyAdapterUse();
        List<QuestionTypeDefinition> definitions = activeOnly
                ? repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc()
                : repository.findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();
        definitions = definitions.stream()
                .filter(definition -> TaskTypeCodeCompatibility.isStandard(
                        definition.getTaskTypeKey() == null ? definition.getCode() : definition.getTaskTypeKey()))
                .toList();
        Map<String, TaskRuntimeProfileDescriptor> profiles = resolveProfiles(definitions);
        return definitions.stream().map(definition -> QuestionTypeMapper.toResponse(
                definition, profiles.get(TaskTypeCodeCompatibility.normalizeForLookup(definition.getCode())))).toList();
    }

    @Transactional(readOnly = true)
    public QuestionTypeResponse get(UUID publicId) {
        markLegacyAdapterUse();
        QuestionTypeDefinition definition = findByPublicId(publicId);
        if (!TaskTypeCodeCompatibility.isStandard(definition.getTaskTypeKey() == null
                ? definition.getCode() : definition.getTaskTypeKey())) {
            throw new QuestionTypeNotFoundException();
        }
        return QuestionTypeMapper.toResponse(definition, resolveProfile(definition.getCode()));
    }

    /** Canonical dynamic catalog projection; unlike the legacy surface it includes custom rows. */
    @Transactional(readOnly = true)
    public TaskTypePageResponse listTaskTypes(boolean activeOnly, String cursor, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        List<QuestionTypeDefinition> definitions = activeOnly
                ? repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc()
                : repository.findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();
        int offset = parseCursor(cursor);
        List<QuestionTypeDefinition> page = definitions.stream()
                .skip(offset).limit(boundedLimit).toList();
        List<QuestionTypeResponse> responses = page.stream()
                .map(definition -> QuestionTypeMapper.toResponse(definition,
                        resolveDynamicContract(definition), isRuntimeLocked(definition)))
                .toList();
        String nextCursor = offset + page.size() < definitions.size()
                ? Integer.toString(offset + page.size()) : null;
        return new TaskTypePageResponse(responses, nextCursor);
    }

    @Transactional(readOnly = true)
    public QuestionTypeResponse getTaskType(UUID publicId) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        return QuestionTypeMapper.toResponse(definition,
                resolveDynamicContract(definition), isRuntimeLocked(definition));
    }

    @Transactional(readOnly = true)
    public List<TaskTypeCapabilityResponse> listCapabilities(boolean activeOnly) {
        return runtimeContractService.listSelectable().stream()
                .filter(contract -> !activeOnly || contract.active())
                .map(TaskTypeCapabilityResponse::from).toList();
    }

    /** Public module-facade lookup used by template and snapshot validation. */
    @Transactional(readOnly = true)
    public TaskRuntimeContractDescriptor resolveRuntimeContract(String taskTypeKey) {
        QuestionTypeDefinition definition = repository.findByTaskTypeKeyAndDeletedFalse(
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey))
                .orElseThrow(QuestionTypeNotFoundException::new);
        TaskRuntimeContractDescriptor contract = resolveDynamicContract(definition);
        if (contract == null) {
            throw new TaskTypeCapabilityNotFoundException();
        }
        return contract;
    }

    @Transactional(readOnly = true)
    public TaskTypeAvailabilityResponse availability(String rawKey, String rawDisplayName, UUID excludePublicId) {
        String key = normalizeKeyForApi(rawKey);
        String name = normalizeDisplayNameForApi(rawDisplayName);
        boolean keyAvailable = key != null && repository.findByTaskTypeKey(key)
                .filter(existing -> !java.util.Objects.equals(existing.getPublicId(), excludePublicId)).isEmpty();
        boolean nameAvailable = name != null && repository.findByNormalizedDisplayName(name)
                .filter(existing -> !java.util.Objects.equals(existing.getPublicId(), excludePublicId)).isEmpty();
        return new TaskTypeAvailabilityResponse(
                new TaskTypeAvailabilityResponse.FieldAvailability(key, keyAvailable,
                        keyAvailable ? null : "TASK_TYPE_KEY_ALREADY_USED"),
                new TaskTypeAvailabilityResponse.FieldAvailability(name, nameAvailable,
                        nameAvailable ? null : "TASK_TYPE_DISPLAY_NAME_ALREADY_USED"));
    }

    @Transactional
    public QuestionTypeResponse createTaskType(CreateTaskTypeRequest request) {
        return createTaskType(request, null);
    }

    @Transactional
    public QuestionTypeResponse createTaskType(CreateTaskTypeRequest request, CurrentUser caller) {
        String taskTypeKey = normalizeKeyForApi(request.taskTypeKey());
        String normalizedDisplayName = normalizeDisplayNameForApi(request.displayName());
        if (repository.findByTaskTypeKey(taskTypeKey).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeKeyAlreadyUsedException();
        }
        if (repository.findByNormalizedDisplayName(normalizedDisplayName).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
        PteSection section = parseSection(request.section());
        TaskRuntimeContractDescriptor contract = resolveContract(request.screenKey(), request.contractVersion());
        QuestionTypeDefinition definition = new QuestionTypeDefinition();
        definition.setCode(taskTypeKey);
        definition.setTaskTypeKey(taskTypeKey);
        definition.setDisplayName(TaskTypeKeyNormalizer.normalizeDisplayLabel(request.displayName()));
        definition.setNormalizedDisplayName(normalizedDisplayName);
        definition.setShortName(request.shortName().trim());
        definition.setSection(section);
        definition.setScored(contract.scoringEnabled());
        definition.setActive(request.active());
        definition.setDisplayOrder(request.displayOrder());
        definition.setLifecycleStatus(Boolean.TRUE.equals(request.active()) ? "ACTIVE" : "INACTIVE");
        applyRuntimeContract(definition, contract);
        try {
            QuestionTypeResponse response = QuestionTypeMapper.toResponse(repository.save(definition), contract, false);
            if (observability != null) {
                observability.dynamicTaskTypeCreated();
            }
            audit(caller, ItembankConstants.TASK_TYPE_CREATED, response.publicId(),
                    "Created task type " + taskTypeKey);
            return response;
        } catch (DataIntegrityViolationException ex) {
            // The database unique indexes are the final race-safe guard. Keep
            // the API contract friendly when two admins submit the same key or
            // display name concurrently.
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeKeyAlreadyUsedException();
        }
    }

    @Transactional
    public QuestionTypeResponse updateTaskType(UUID publicId, UpdateTaskTypeRequest request) {
        return updateTaskType(publicId, request, null);
    }

    @Transactional
    public QuestionTypeResponse updateTaskType(UUID publicId, UpdateTaskTypeRequest request, CurrentUser caller) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        String taskTypeKey = definition.getTaskTypeKey() == null ? definition.getCode() : definition.getTaskTypeKey();
        String normalizedDisplayName = normalizeDisplayNameForApi(request.displayName());
        if (repository.findByNormalizedDisplayName(normalizedDisplayName)
                .filter(existing -> !java.util.Objects.equals(existing.getPublicId(), publicId)).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
        boolean runtimeChange = request.screenKey() != null || request.contractVersion() != null
                || request.section() != null;
        if (runtimeChange && isRuntimeLocked(definition)) {
            if (auditLogService != null && caller != null) {
                auditLogService.recordFailure(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                        String.valueOf(publicId), ItembankConstants.TASK_TYPE_RUNTIME_LOCK_CONFLICT,
                        "Runtime fields are locked for task type " + taskTypeKey);
            }
            throw new TaskTypeRuntimeLockedException(taskTypeKey);
        }
        if (runtimeChange && (request.screenKey() == null || request.contractVersion() == null
                || request.section() == null || request.section().isBlank())) {
            throw new TaskTypeCapabilityNotFoundException();
        }
        definition.setDisplayName(TaskTypeKeyNormalizer.normalizeDisplayLabel(request.displayName()));
        definition.setNormalizedDisplayName(normalizedDisplayName);
        definition.setShortName(request.shortName().trim());
        definition.setDisplayOrder(request.displayOrder());
        definition.setActive(request.active());
        definition.setLifecycleStatus(Boolean.TRUE.equals(request.active()) ? "ACTIVE" : "INACTIVE");
        if (runtimeChange) {
            PteSection section = parseSection(request.section());
            TaskRuntimeContractDescriptor contract = resolveContract(request.screenKey(), request.contractVersion());
            definition.setSection(section);
            definition.setScored(contract.scoringEnabled());
            applyRuntimeContract(definition, contract);
        }
        try {
            QuestionTypeResponse response = QuestionTypeMapper.toResponse(repository.save(definition),
                    resolveDynamicContract(definition), isRuntimeLocked(definition));
            audit(caller, ItembankConstants.TASK_TYPE_UPDATED, publicId,
                    "Updated task type metadata " + taskTypeKey);
            if (runtimeChange) {
                audit(caller, ItembankConstants.TASK_TYPE_RUNTIME_UPDATED, publicId,
                        "Updated runtime contract for task type " + taskTypeKey);
            }
            return response;
        } catch (DataIntegrityViolationException ex) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
    }

    @Transactional
    public void retireTaskType(UUID publicId) {
        retireTaskType(publicId, null);
    }

    @Transactional
    public void retireTaskType(UUID publicId, CurrentUser caller) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        definition.setActive(false);
        definition.setLifecycleStatus("RETIRED");
        repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_RETIRED, publicId,
                "Retired task type " + (definition.getTaskTypeKey() == null ? definition.getCode()
                        : definition.getTaskTypeKey()));
    }

    /**
     * Lists standard task codes that an administrator can add to the persisted
     * catalog through the UI. This keeps the compatibility enum on the server
     * instead of duplicating it in a frontend bundle.
     */
    @Transactional(readOnly = true)
    public List<SupportedQuestionTypeResponse> listSupported() {
        return Arrays.stream(PteTaskType.values())
                .map(taskType -> new SupportedQuestionTypeResponse(
                        taskType.name(), taskType.getSection(), taskType.isScored()))
                .toList();
    }

    /**
     * Creates or restores one standard PTE task type.
     *
     * <p>Question rows store {@link PteTaskType} as an enum-backed integration
     * key, so accepting arbitrary catalog codes here would create types that
     * the question bank cannot author. The server therefore owns the canonical
     * section/scoring/authoring metadata for every supported code.
     */
    @Transactional
    public QuestionTypeResponse create(CreateQuestionTypeRequest request) {
        String code = TaskTypeCodeCompatibility.requireCanonicalCode(request.code());
        PteTaskType taskType = TaskTypeCodeCompatibility.parse(code);
        validateSection(taskType, request.section());

        QuestionTypeDefinition definition = repository.findByCode(code)
                .map(existing -> {
                    if (!existing.isDeleted()) {
                        throw new QuestionTypeCodeAlreadyUsedException();
                    }
                    return existing;
                })
                .orElseGet(QuestionTypeDefinition::new);

        definition.setDeleted(false);
        definition.setCode(code);
        definition.setTaskTypeKey(code);
        definition.setDisplayName(request.displayName().trim());
        definition.setNormalizedDisplayName(TaskTypeKeyNormalizer.normalizeDisplayName(request.displayName()));
        definition.setShortName(request.shortName().trim());
        definition.setSection(taskType.getSection());
        definition.setScored(taskType.isScored());
        definition.setActive(request.active());
        definition.setDisplayOrder(request.displayOrder());
        applyCanonicalRequirements(definition, taskType);

        definition = repository.save(definition);
        return QuestionTypeMapper.toResponse(definition, resolveProfile(definition.getCode()));
    }

    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateQuestionTypeRequest request) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        definition.setDisplayName(request.displayName().trim());
        definition.setShortName(request.shortName().trim());
        definition.setDisplayOrder(request.displayOrder());
        definition.setActive(request.active());
        applyCanonicalRequirements(definition, TaskTypeCodeCompatibility.parse(definition.getCode()));
        definition = repository.save(definition);
        return QuestionTypeMapper.toResponse(definition, resolveProfile(definition.getCode()));
    }

    /** Soft-deletes a type so existing question rows keep their stable FK key. */
    @Transactional
    public void delete(UUID publicId) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        definition.setActive(false);
        definition.setDeleted(true);
        repository.save(definition);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionTypeDefinition> findDefinitionByCode(String code) {
        // A deleted catalog row must remain readable by validation/delivery so
        // existing questions keep their authored behavior and stable code.
        return repository.findByCode(TaskTypeCodeCompatibility.normalizeForLookup(code));
    }

    @Transactional(readOnly = true)
    public boolean isActive(String code) {
        return repository.findByCodeAndDeletedFalse(TaskTypeCodeCompatibility.normalizeForLookup(code))
                .map(QuestionTypeDefinition::isActive).orElse(false);
    }

    private QuestionTypeDefinition findByPublicId(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(QuestionTypeNotFoundException::new);
    }

    private void validateSection(PteTaskType taskType, String section) {
        if (section == null) {
            throw new InvalidQuestionTypeException();
        }
        final PteSection requestedSection;
        try {
            requestedSection = PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuestionTypeException();
        }
        if (taskType.getSection() != requestedSection) {
            throw new InvalidQuestionTypeException();
        }
    }

    private void applyCanonicalRequirements(QuestionTypeDefinition definition, PteTaskType taskType) {
        definition.setRequiresAudioPrompt(taskType.requiresAudioPrompt());
        definition.setRequiresImagePrompt(taskType.requiresImagePrompt());
        definition.setRequiresPromptText(taskType.requiresPromptText());
        definition.setRequiresOptions(taskType.requiresOptions());
        definition.setRequiresCorrectAnswer(taskType.requiresCorrectAnswer());
        definition.setRequiresWordCount(taskType.requiresWordCount());
        definition.setRequiresSingleCorrectOption(
                taskType == PteTaskType.MC_READING_SINGLE || taskType == PteTaskType.MC_LISTENING_SINGLE);
        definition.setUsesOptionOrderAsCorrectPosition(taskType == PteTaskType.RE_ORDER_PARAGRAPHS);
    }

    /**
     * Public module-facade lookup for consumers that only need the catalog
     * section. Do not leak the persistence entity across the itembank boundary.
     */
    @Transactional(readOnly = true)
    public Optional<String> findSectionByTaskTypeKey(String taskTypeKey) {
        String normalizedKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey);
        return repository.findByTaskTypeKeyAndDeletedFalse(normalizedKey)
                .map(QuestionTypeDefinition::getSection)
                .map(Enum::name);
    }

    private Map<String, TaskRuntimeProfileDescriptor> resolveProfiles(List<QuestionTypeDefinition> definitions) {
        if (definitions.isEmpty()) {
            return Map.of();
        }
        if (runtimeProfileService == null) {
            Map<String, TaskRuntimeProfileDescriptor> fallback = new LinkedHashMap<>();
            definitions.forEach(definition -> fallback.put(definition.getCode(),
                    TaskRuntimeProfileRegistry.descriptorFor(definition.getCode())));
            return fallback;
        }
        return runtimeProfileService.resolveActiveByTaskTypeCodes(
                definitions.stream().map(QuestionTypeDefinition::getCode).toList());
    }

    private TaskRuntimeProfileDescriptor resolveProfile(String code) {
        return runtimeProfileService == null
                ? TaskRuntimeProfileRegistry.descriptorFor(code)
                : runtimeProfileService.resolveActive(code);
    }

    private TaskRuntimeContractDescriptor resolveDynamicContract(QuestionTypeDefinition definition) {
        if (definition.getScreenKey() != null && definition.getRuntimeProfileVersion() != null
                && runtimeContractService != null) {
            try {
                return runtimeContractService.resolveReadable(definition.getScreenKey(),
                        definition.getRuntimeProfileVersion());
            } catch (RuntimeException ignored) {
                // A retired contract remains readable through the stored binding;
                // the canonical response marks it unavailable for new authoring.
            }
        }
        if (runtimeContractService != null && definition.getTaskTypeKey() != null
                && TaskTypeCodeCompatibility.isStandard(definition.getTaskTypeKey())) {
            return runtimeContractService.fromLegacyProfile(definition.getTaskTypeKey());
        }
        return null;
    }

    private TaskRuntimeContractDescriptor resolveContract(String screenKey, int contractVersion) {
        try {
            return runtimeContractService.resolveActive(screenKey, contractVersion);
        } catch (RuntimeException ex) {
            throw new TaskTypeCapabilityNotFoundException();
        }
    }

    private void applyRuntimeContract(QuestionTypeDefinition definition,
            TaskRuntimeContractDescriptor contract) {
        definition.setScreenKey(contract.screenKey());
        definition.setRuntimeProfileKey(contract.profileKey());
        definition.setRuntimeProfileVersion(contract.profileVersion());
        definition.setRuntimeBehaviorKey(contract.behaviorKey());
        definition.setRuntimeRendererKey(contract.rendererKey());
        definition.setRuntimeAnswerSchemaVersion(contract.answerSchemaVersion());
        definition.setRuntimeScoringProfileKey(contract.scoringProfileKey());
        definition.setRuntimeScoringProfileVersion(contract.scoringProfileVersion());
        definition.setRuntimeScoringMode(contract.scoringMode());
        definition.setRuntimeRequiredCapabilities(contract.requiredClientCapabilities().toArray(String[]::new));
        definition.setRuntimeMinAppVersion(contract.minSupportedAppVersion());
        definition.setAuthoringContractKey(contract.authoringContractKey());
        definition.setAuthoringContractVersion(contract.authoringContractVersion());
        TaskAuthoringRequirements requirements = contract.authoringRequirements();
        definition.setRequiresAudioPrompt(requirements.requiresAudioPrompt());
        definition.setRequiresImagePrompt(requirements.requiresImagePrompt());
        definition.setRequiresPromptText(requirements.requiresPromptText());
        definition.setRequiresOptions(requirements.requiresOptions());
        definition.setRequiresCorrectAnswer(requirements.requiresCorrectAnswer());
        definition.setRequiresWordCount(requirements.requiresWordCount());
        definition.setRequiresSingleCorrectOption(requirements.requiresSingleCorrectOption());
        definition.setUsesOptionOrderAsCorrectPosition(requirements.usesOptionOrderAsCorrectPosition());
    }

    private boolean isRuntimeLocked(QuestionTypeDefinition definition) {
        return definition.getRuntimeLockedAt() != null
                || (publicationUsageService != null
                && publicationUsageService.isRuntimeLocked(definition.getTaskTypeKey() == null
                ? definition.getCode() : definition.getTaskTypeKey()));
    }

    private void markLegacyAdapterUse() {
        if (observability != null) {
            observability.legacyTaskTypeAdapterUsed();
        }
    }

    private void markDuplicate(CurrentUser caller, String taskTypeKey) {
        if (observability != null) {
            observability.duplicateTaskTypeRejected();
        }
        audit(caller, ItembankConstants.TASK_TYPE_DUPLICATE_REJECTED, taskTypeKey,
                "A task type key or display name was already used");
    }

    private void audit(CurrentUser caller, String action, Object aggregateId, String summary) {
        if (auditLogService == null || caller == null) {
            return;
        }
        auditLogService.record(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                String.valueOf(aggregateId), action, summary.length() <= 500 ? summary : summary.substring(0, 500));
    }

    private String normalizeKeyForApi(String rawKey) {
        try {
            return rawKey == null ? null : TaskTypeKeyNormalizer.normalizeKey(rawKey);
        } catch (RuntimeException ex) {
            throw new TaskTypeKeyInvalidException();
        }
    }

    private String normalizeDisplayNameForApi(String rawDisplayName) {
        try {
            return rawDisplayName == null ? null : TaskTypeKeyNormalizer.normalizeDisplayName(rawDisplayName);
        } catch (RuntimeException ex) {
            throw new TaskTypeDisplayNameInvalidException();
        }
    }

    private PteSection parseSection(String section) {
        try {
            return PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new InvalidQuestionTypeException();
        }
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

}
