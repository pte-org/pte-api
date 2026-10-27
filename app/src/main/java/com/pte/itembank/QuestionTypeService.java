package com.pte.itembank;

import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypeAvailabilityResponse;
import com.pte.itembank.dto.response.TaskTypeCapabilityResponse;
import com.pte.itembank.dto.response.TaskTypePageResponse;
import com.pte.itembank.internal.service.tasktype.lifecycle.DynamicTaskTypeService;
import com.pte.itembank.internal.service.tasktype.lifecycle.StandardTaskTypeService;
import com.pte.itembank.internal.service.tasktype.lifecycle.TaskTypeGovernanceService;
import com.pte.itembank.internal.service.tasktype.TaskTypeCapabilityService;
import com.pte.itembank.internal.service.tasktype.TaskTypeQueryService;
import com.pte.itembank.internal.service.tasktype.TaskTypeRuntimeLockingService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public module facade for the question-type catalog. Routes to specialized
 * services that handle different aspects of task-type management: queries,
 * standard type lifecycle, custom type lifecycle, and governance.
 */
@Service
public class QuestionTypeService {

    private final TaskTypeQueryService queryService;
    private final TaskTypeCapabilityService capabilityService;
    private final StandardTaskTypeService standardTaskTypeService;
    private final DynamicTaskTypeService dynamicTaskTypeService;
    private final TaskTypeGovernanceService governanceService;
    private final TaskTypeRuntimeLockingService runtimeLockingService;

    @Autowired
    public QuestionTypeService(
            TaskTypeQueryService queryService,
            TaskTypeCapabilityService capabilityService,
            StandardTaskTypeService standardTaskTypeService,
            DynamicTaskTypeService dynamicTaskTypeService,
            TaskTypeGovernanceService governanceService,
            TaskTypeRuntimeLockingService runtimeLockingService) {
        this.queryService = queryService;
        this.capabilityService = capabilityService;
        this.standardTaskTypeService = standardTaskTypeService;
        this.dynamicTaskTypeService = dynamicTaskTypeService;
        this.governanceService = governanceService;
        this.runtimeLockingService = runtimeLockingService;
    }

    // ========== Legacy catalog (standard types only) ==========

    @Transactional(readOnly = true)
    public List<QuestionTypeResponse> list(boolean activeOnly) {
        return queryService.listLegacy(activeOnly);
    }

    @Transactional(readOnly = true)
    public QuestionTypeResponse get(UUID publicId) {
        return queryService.getLegacy(publicId);
    }

    // ========== Dynamic catalog (standard + custom types) ==========

    @Transactional(readOnly = true)
    public TaskTypePageResponse listTaskTypes(boolean activeOnly, String cursor, int limit) {
        return queryService.listDynamic(activeOnly, cursor, limit);
    }

    @Transactional(readOnly = true)
    public QuestionTypeResponse getTaskType(UUID publicId) {
        return queryService.getDynamic(publicId);
    }

    // ========== Capability queries ==========

    @Transactional(readOnly = true)
    public List<TaskTypeCapabilityResponse> listCapabilities(boolean activeOnly) {
        return capabilityService.listCapabilities(activeOnly);
    }

    @Transactional(readOnly = true)
    public TaskRuntimeContractDescriptor resolveRuntimeContract(String taskTypeKey) {
        return queryService.resolveRuntimeContract(taskTypeKey);
    }

    @Transactional(readOnly = true)
    public TaskTypeAvailabilityResponse availability(String rawKey, String rawDisplayName, UUID excludePublicId) {
        return capabilityService.checkAvailability(rawKey, rawDisplayName, excludePublicId);
    }

    // ========== Dynamic task type operations ==========

    @Transactional
    public QuestionTypeResponse createTaskType(CreateTaskTypeRequest request) {
        return createTaskType(request, null);
    }

    @Transactional
    public QuestionTypeResponse createTaskType(CreateTaskTypeRequest request, CurrentUser caller) {
        return dynamicTaskTypeService.create(request, caller);
    }

    @Transactional
    public QuestionTypeResponse updateTaskType(UUID publicId, UpdateTaskTypeRequest request) {
        return updateTaskType(publicId, request, null);
    }

    @Transactional
    public QuestionTypeResponse updateTaskType(UUID publicId, UpdateTaskTypeRequest request, CurrentUser caller) {
        return dynamicTaskTypeService.update(publicId, request, caller);
    }

    @Transactional
    public QuestionTypeResponse submitTaskType(UUID publicId, CurrentUser caller) {
        return governanceService.submit(publicId, caller);
    }

    @Transactional
    public QuestionTypeResponse approveTaskType(UUID publicId, CurrentUser caller) {
        return governanceService.approve(publicId, caller);
    }

    @Transactional
    public void retireTaskType(UUID publicId) {
        retireTaskType(publicId, null);
    }

    @Transactional
    public void retireTaskType(UUID publicId, CurrentUser caller) {
        governanceService.retire(publicId, caller);
    }

    // ========== Standard task type operations ==========

    @Transactional(readOnly = true)
    public List<SupportedQuestionTypeResponse> listSupported() {
        return queryService.listSupported();
    }

    @Transactional
    public QuestionTypeResponse create(CreateQuestionTypeRequest request) {
        return create(request, null);
    }

    @Transactional
    public QuestionTypeResponse create(CreateQuestionTypeRequest request, CurrentUser caller) {
        return standardTaskTypeService.create(request, caller);
    }

    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateQuestionTypeRequest request) {
        return update(publicId, request, null);
    }

    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateQuestionTypeRequest request, CurrentUser caller) {
        return standardTaskTypeService.update(publicId, request, caller);
    }

    @Transactional
    public void delete(UUID publicId) {
        delete(publicId, null);
    }

    @Transactional
    public void delete(UUID publicId, CurrentUser caller) {
        standardTaskTypeService.delete(publicId, caller);
    }

    // ========== Lookups ==========

    @Transactional(readOnly = true)
    public Optional<com.pte.itembank.domain.QuestionType> findDefinitionByCode(String code) {
        return queryService.findDefinitionByCode(code);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String code) {
        return queryService.isActive(code);
    }

    @Transactional(readOnly = true)
    public Optional<String> findSectionByTaskTypeKey(String taskTypeKey) {
        return queryService.findSectionByTaskTypeKey(taskTypeKey);
    }

    // ========== Runtime locking ==========

    @Transactional
    public void recordPublishedUse(Collection<String> taskTypeKeys, Instant publishedAt) {
        runtimeLockingService.recordPublishedUse(taskTypeKeys, publishedAt);
    }
}
