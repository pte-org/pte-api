package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypePageResponse;
import com.pte.itembank.internal.exception.tasktype.QuestionTypeNotFoundException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeCapabilityNotFoundException;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeObservability;
import com.pte.itembank.domain.enums.PteTaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only queries over the persisted task-type catalog: standard types,
 * dynamic custom types, and their runtime metadata.
 */
@Service
public class TaskTypeQueryService {

    private final QuestionTypeRepository repository;
    private final RuntimeProfileResolver profileResolver;
    private final RuntimeContractResolver contractResolver;
    private final TaskTypeObservability observability;

    public TaskTypeQueryService(
            QuestionTypeRepository repository,
            RuntimeProfileResolver profileResolver,
            RuntimeContractResolver contractResolver,
            TaskTypeObservability observability) {
        this.repository = repository;
        this.profileResolver = profileResolver;
        this.contractResolver = contractResolver;
        this.observability = observability;
    }

    /**
     * Legacy list endpoint for authored catalog, filtered to standard types only.
     */
    @Transactional(readOnly = true)
    public List<QuestionTypeResponse> listLegacy(boolean activeOnly) {
        if (observability != null) {
            observability.legacyTaskTypeAdapterUsed();
        }
        List<QuestionType> definitions = activeOnly
                ? repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc()
                : repository.findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();
        definitions = definitions.stream()
                .filter(definition -> TaskTypeCodeCompatibility.isStandard(
                        definition.getTaskTypeKey() == null ? definition.getCode() : definition.getTaskTypeKey()))
                .toList();
        Map<String, TaskRuntimeProfileDescriptor> profiles = profileResolver.resolveProfileMap(definitions);
        return definitions.stream().map(definition -> QuestionTypeMapper.toResponse(
                definition, profiles.get(TaskTypeCodeCompatibility.normalizeForLookup(definition.getCode())))).toList();
    }

    /**
     * Legacy get endpoint for authored catalog, filtered to standard types only.
     */
    @Transactional(readOnly = true)
    public QuestionTypeResponse getLegacy(UUID publicId) {
        if (observability != null) {
            observability.legacyTaskTypeAdapterUsed();
        }
        QuestionType definition = findByPublicIdOrThrow(publicId);
        if (!TaskTypeCodeCompatibility.isStandard(definition.getTaskTypeKey() == null
                ? definition.getCode() : definition.getTaskTypeKey())) {
            throw new QuestionTypeNotFoundException();
        }
        return QuestionTypeMapper.toResponse(definition, profileResolver.resolveProfile(definition.getCode()));
    }

    /**
     * Canonical dynamic catalog projection; includes both standard and custom types.
     */
    @Transactional(readOnly = true)
    public TaskTypePageResponse listDynamic(boolean activeOnly, String cursor, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        List<QuestionType> definitions = activeOnly
                ? repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc()
                : repository.findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();
        int offset = parseCursor(cursor);
        List<QuestionType> page = definitions.stream()
                .skip(offset).limit(boundedLimit).toList();
        List<QuestionTypeResponse> responses = page.stream()
                .map(definition -> QuestionTypeMapper.toResponse(definition,
                        contractResolver.resolveDynamic(definition), isRuntimeLocked(definition)))
                .toList();
        String nextCursor = offset + page.size() < definitions.size()
                ? Integer.toString(offset + page.size()) : null;
        return new TaskTypePageResponse(responses, nextCursor);
    }

    /**
     * Get a single task type from the canonical dynamic catalog.
     */
    @Transactional(readOnly = true)
    public QuestionTypeResponse getDynamic(UUID publicId) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        return QuestionTypeMapper.toResponse(definition,
                contractResolver.resolveDynamic(definition), isRuntimeLocked(definition));
    }

    /**
     * Resolve runtime contract for template/snapshot validation.
     */
    @Transactional(readOnly = true)
    public TaskRuntimeContractDescriptor resolveRuntimeContract(String taskTypeKey) {
        QuestionType definition = repository.findByTaskTypeKeyAndDeletedFalse(
                        TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey))
                .orElseThrow(QuestionTypeNotFoundException::new);
        TaskRuntimeContractDescriptor contract = contractResolver.resolveDynamic(definition);
        if (contract == null) {
            throw new TaskTypeCapabilityNotFoundException();
        }
        return contract;
    }

    /**
     * List all standard task types that can be added through the admin UI.
     */
    @Transactional(readOnly = true)
    public List<SupportedQuestionTypeResponse> listSupported() {
        return Arrays.stream(PteTaskType.values())
                .map(taskType -> new SupportedQuestionTypeResponse(
                        taskType.name(), taskType.getSection(), taskType.isScored()))
                .toList();
    }

    /**
     * Look up a task type definition by code (may be deleted).
     */
    @Transactional(readOnly = true)
    public Optional<QuestionType> findDefinitionByCode(String code) {
        return repository.findByCode(TaskTypeCodeCompatibility.normalizeForLookup(code));
    }

    /**
     * Check whether a task type is active.
     */
    @Transactional(readOnly = true)
    public boolean isActive(String code) {
        return repository.findByCodeAndDeletedFalse(TaskTypeCodeCompatibility.normalizeForLookup(code))
                .map(QuestionType::isActive).orElse(false);
    }

    /**
     * Resolve the PTE section for a given task type key.
     */
    @Transactional(readOnly = true)
    public Optional<String> findSectionByTaskTypeKey(String taskTypeKey) {
        String normalizedKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey);
        return repository.findByTaskTypeKeyAndDeletedFalse(normalizedKey)
                .map(QuestionType::getSection)
                .map(Enum::name);
    }

    QuestionType findByPublicIdOrThrow(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(QuestionTypeNotFoundException::new);
    }

    private boolean isRuntimeLocked(QuestionType definition) {
        return definition.getRuntimeLockedAt() != null;
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
