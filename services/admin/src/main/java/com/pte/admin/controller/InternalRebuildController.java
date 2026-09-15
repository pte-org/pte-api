package com.pte.admin.controller;

import com.pte.admin.service.StudentRosterRebuildService;
import com.pte.admin.service.StudentRosterRebuildService.RebuildSummary;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal operator endpoint for rebuilding Admin's student roster projection. */
@RestController
@RequestMapping("/internal/rebuild")
@PreAuthorize("hasRole('INTERNAL_SERVICE_BOOTSTRAP')")
public class InternalRebuildController {

    private final StudentRosterRebuildService rebuildService;

    public InternalRebuildController(StudentRosterRebuildService rebuildService) {
        this.rebuildService = rebuildService;
    }

    @PostMapping("/students")
    public ApiResponse<RebuildSummary> rebuildStudents() {
        return ApiResponse.success(rebuildService.rebuildAll());
    }
}
