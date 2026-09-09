package com.pte.admin.controller;

import com.pte.admin.dto.response.ClassMembershipResponse;
import com.pte.admin.service.ClassService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-wide student-roster listing, optionally filtered by Program — the
 * shared data source Phase 7 (search), Phase 10 (bulk exam-session roster
 * resolution) and Phase 13 (dashboard) all reuse instead of each adding their
 * own variant. Always forwards {@code caller.tenantId()} into the service
 * call — never trusts a tenant id from the request.
 */
@RestController
@RequestMapping("/class-memberships")
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
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
