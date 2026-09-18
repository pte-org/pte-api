package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.response.StudentRosterRowResponse;
import com.pte.enrollment.internal.service.StudentRosterQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tenant-scoped server-side student roster endpoint. */
@RestController
@RequestMapping("/api/v1/student-roster")
@PreAuthorize("hasRole('HOST_ADMIN')")
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
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(queryService.search(page, size, search, programPublicId, classPublicId,
                assignmentStatus, sort, direction, caller));
    }
}
