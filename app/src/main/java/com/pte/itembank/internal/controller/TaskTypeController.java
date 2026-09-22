package com.pte.itembank.internal.controller;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeRolloutProperties;
import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypeAvailabilityResponse;
import com.pte.itembank.dto.response.TaskTypeCapabilityResponse;
import com.pte.itembank.dto.response.TaskTypePageResponse;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Canonical logical task-type catalog; implementation details stay server-owned. */
@RestController
@RequestMapping("/api/v1/task-types")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
public class TaskTypeController {

    private final QuestionTypeService service;
    private final TaskTypeRolloutProperties rolloutProperties;

    public TaskTypeController(QuestionTypeService service, TaskTypeRolloutProperties rolloutProperties) {
        this.service = service;
        this.rolloutProperties = rolloutProperties;
    }

    @GetMapping
    public ApiResponse<TaskTypePageResponse> list(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(service.listTaskTypes(activeOnly, cursor, limit));
    }

    @GetMapping("/capabilities")
    public ApiResponse<List<TaskTypeCapabilityResponse>> listCapabilities(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.success(service.listCapabilities(activeOnly));
    }

    @GetMapping("/availability")
    public ApiResponse<TaskTypeAvailabilityResponse> availability(
            @RequestParam(required = false) String taskTypeKey,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) UUID excludePublicId) {
        return ApiResponse.success(service.availability(taskTypeKey, displayName, excludePublicId));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<QuestionTypeResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getTaskType(publicId));
    }

    @PostMapping
    public ApiResponse<QuestionTypeResponse> create(@Valid @RequestBody CreateTaskTypeRequest request) {
        if (!rolloutProperties.isCustomCreationEnabled()
                && !TaskTypeCodeCompatibility.isStandard(request.taskTypeKey())) {
            throw new com.pte.itembank.internal.exception.TaskTypeCustomCreationDisabledException();
        }
        return ApiResponse.success(service.createTaskType(request, currentUser()));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<QuestionTypeResponse> update(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateTaskTypeRequest request) {
        return ApiResponse.success(service.updateTaskType(publicId, request, currentUser()));
    }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> retire(@PathVariable UUID publicId) {
        service.retireTaskType(publicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
