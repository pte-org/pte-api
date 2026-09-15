package com.pte.admin.controller;

import com.pte.admin.dto.response.StudentRosterRowResponse;
import com.pte.admin.service.StudentRosterQueryService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.common.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tenant-scoped server-side student roster endpoint. */
@RestController
@RequestMapping("/student-roster")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class StudentRosterController {

    private final StudentRosterQueryService queryService;

    public StudentRosterController(StudentRosterQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<PagedResult<StudentRosterRowResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID programPublicId,
            @RequestParam(required = false) UUID classPublicId,
            @RequestParam(required = false) String assignmentStatus,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        CurrentUser caller = CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
        return ApiResponse.success(queryService.search(page, size, search, programPublicId, classPublicId,
                assignmentStatus, sort, direction, caller));
    }
}
