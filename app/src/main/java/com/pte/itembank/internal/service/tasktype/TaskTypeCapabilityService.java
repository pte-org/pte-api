package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.dto.response.TaskTypeAvailabilityResponse;
import com.pte.itembank.dto.response.TaskTypeCapabilityResponse;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.TaskTypeKeyNormalizer;
import com.pte.itembank.TaskRuntimeContractService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Validates task-type availability and exposes capability metadata for
 * authoring clients.
 */
@Service
public class TaskTypeCapabilityService {

    private final QuestionTypeRepository repository;
    private final TaskRuntimeContractService contractService;

    public TaskTypeCapabilityService(
            QuestionTypeRepository repository,
            TaskRuntimeContractService contractService) {
        this.repository = repository;
        this.contractService = contractService;
    }

    /**
     * List all selectable runtime capabilities.
     */
    @Transactional(readOnly = true)
    public List<TaskTypeCapabilityResponse> listCapabilities(boolean activeOnly) {
        return contractService.listSelectable().stream()
                .filter(contract -> !activeOnly || contract.active())
                .map(TaskTypeCapabilityResponse::from).toList();
    }

    /**
     * Check availability of task-type key and display name for creation/update.
     */
    @Transactional(readOnly = true)
    public TaskTypeAvailabilityResponse checkAvailability(
            String rawKey, String rawDisplayName, UUID excludePublicId) {
        String key = normalizeKeyForApi(rawKey);
        String name = normalizeDisplayNameForApi(rawDisplayName);
        boolean keyAvailable = key != null && repository.findByTaskTypeKey(key)
                .filter(existing -> !Objects.equals(existing.getPublicId(), excludePublicId)).isEmpty();
        boolean nameAvailable = name != null && repository.findByNormalizedDisplayName(name)
                .filter(existing -> !Objects.equals(existing.getPublicId(), excludePublicId)).isEmpty();
        return new TaskTypeAvailabilityResponse(
                new TaskTypeAvailabilityResponse.FieldAvailability(key, keyAvailable,
                        keyAvailable ? null : "TASK_TYPE_KEY_ALREADY_USED"),
                new TaskTypeAvailabilityResponse.FieldAvailability(name, nameAvailable,
                        nameAvailable ? null : "TASK_TYPE_DISPLAY_NAME_ALREADY_USED"));
    }

    private String normalizeKeyForApi(String rawKey) {
        try {
            return rawKey == null ? null : TaskTypeKeyNormalizer.normalizeKey(rawKey);
        } catch (RuntimeException ex) {
            throw new com.pte.itembank.internal.exception.tasktype.TaskTypeKeyInvalidException();
        }
    }

    private String normalizeDisplayNameForApi(String rawDisplayName) {
        try {
            return rawDisplayName == null ? null : TaskTypeKeyNormalizer.normalizeDisplayName(rawDisplayName);
        } catch (RuntimeException ex) {
            throw new com.pte.itembank.internal.exception.tasktype.TaskTypeDisplayNameInvalidException();
        }
    }
}
