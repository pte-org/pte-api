package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;
import com.pte.enrollment.internal.service.ClassService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-wide student-roster listing, optionally filtered by Program â€” the
 * shared data source Phase 7 (search), Phase 10 (bulk exam-session roster
 * resolution) and Phase 13 (dashboard) all reuse instead of each adding their
 * own variant. Always forwards {@code caller.tenantId()} into the service
 * call â€” never trusts a tenant id from the request.
 */
@RestController
@RequestMapping("/api/v1/class-memberships")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ClassMembershipController {

    private final ClassService classService;

    public ClassMembershipController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    public ApiResponse<List<ClassMembershipResponse>> list(
            @RequestParam(required = false) UUID programPublicId) {
        return ApiResponse.success(classService.listMemberships(programPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
