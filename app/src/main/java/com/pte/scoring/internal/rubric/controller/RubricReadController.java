package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.dto.TaskTypeRubricResponse;
import com.pte.scoring.internal.rubric.service.RubricReadService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Vendor-only read API of the platform rubric (vendor-web rubric page). Host and other tenant roles have no access;
 * hosts see the pinned version's parameters inside the score explanation instead (phase 6).
 */
@RestController
@RequestMapping(RubricConstants.RUBRIC_API_PATH)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class RubricReadController {

    private final RubricReadService readService;

    public RubricReadController(RubricReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/task-types")
    public ApiResponse<List<TaskTypeRubricResponse>> listTaskTypes(
            @RequestParam(name = "versionId", required = false) UUID versionId) {
        return ApiResponse.success(readService.listTaskTypes(versionId));
    }

    @GetMapping("/task-types/{taskType}")
    public ApiResponse<TaskTypeRubricResponse> getTaskType(@PathVariable String taskType,
            @RequestParam(name = "versionId", required = false) UUID versionId) {
        return ApiResponse.success(readService.getTaskType(taskType, versionId));
    }

    @GetMapping("/versions")
    public ApiResponse<List<RubricVersionResponse>> listVersions() {
        return ApiResponse.success(readService.listVersions());
    }

    @GetMapping("/versions/{publicId}")
    public ApiResponse<RubricVersionResponse> getVersion(@PathVariable UUID publicId) {
        return ApiResponse.success(readService.getVersion(publicId));
    }
}
