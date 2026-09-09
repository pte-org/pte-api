package com.pte.admin.controller;

import com.pte.admin.dto.request.AssignStudentRequest;
import com.pte.admin.dto.request.BulkAssignStudentsRequest;
import com.pte.admin.dto.request.CreateClassRequest;
import com.pte.admin.dto.request.TransferStudentRequest;
import com.pte.admin.dto.request.UpdateClassRequest;
import com.pte.admin.dto.response.BulkAssignStudentsResponse;
import com.pte.admin.dto.response.ClassMembershipResponse;
import com.pte.admin.dto.response.ClassResponse;
import com.pte.admin.service.ClassService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Host self-service Class (Lớp) CRUD + student assignment, scoped to the caller's own tenant via {@code caller.tenantId()}. */
@RestController
@RequestMapping("/organizations/{organizationPublicId}/programs/{programPublicId}/classes")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @PostMapping
    public ApiResponse<ClassResponse> create(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @Valid @RequestBody CreateClassRequest request) {
        return ApiResponse.success(classService.create(organizationPublicId, programPublicId, request, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<ClassResponse>> list(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId) {
        return ApiResponse.success(classService.list(organizationPublicId, programPublicId, currentUser()));
    }

    @GetMapping("/{classPublicId}")
    public ApiResponse<ClassResponse> get(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(classService.get(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @PutMapping("/{classPublicId}")
    public ApiResponse<ClassResponse> update(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId,
            @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.success(
                classService.update(organizationPublicId, programPublicId, classPublicId, request, currentUser()));
    }

    @PostMapping("/{classPublicId}/activate")
    public ApiResponse<ClassResponse> activate(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(
                classService.activate(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @PostMapping("/{classPublicId}/deactivate")
    public ApiResponse<ClassResponse> deactivate(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(
                classService.deactivate(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @PostMapping("/{classPublicId}/suspend")
    public ApiResponse<ClassResponse> suspend(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(
                classService.suspend(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @PostMapping("/{classPublicId}/archive")
    public ApiResponse<ClassResponse> archive(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(
                classService.archive(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @PostMapping("/{classPublicId}/students")
    public ApiResponse<ClassMembershipResponse> assign(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId,
            @Valid @RequestBody AssignStudentRequest request) {
        return ApiResponse.success(
                classService.assign(organizationPublicId, programPublicId, classPublicId, request, currentUser()));
    }

    @PostMapping("/{classPublicId}/students/bulk")
    public ApiResponse<BulkAssignStudentsResponse> bulkAssign(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId,
            @Valid @RequestBody BulkAssignStudentsRequest request) {
        return ApiResponse.success(
                classService.bulkAssign(organizationPublicId, programPublicId, classPublicId, request, currentUser()));
    }

    @DeleteMapping("/{classPublicId}/students/{membershipPublicId}")
    public ApiResponse<Void> unassign(@PathVariable UUID organizationPublicId, @PathVariable UUID programPublicId,
            @PathVariable UUID classPublicId, @PathVariable UUID membershipPublicId) {
        classService.unassign(organizationPublicId, programPublicId, classPublicId, membershipPublicId, currentUser());
        return ApiResponse.success(null);
    }

    @PostMapping("/{classPublicId}/students/{membershipPublicId}/transfer")
    public ApiResponse<ClassMembershipResponse> transfer(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId,
            @PathVariable UUID membershipPublicId, @Valid @RequestBody TransferStudentRequest request) {
        return ApiResponse.success(classService.transfer(organizationPublicId, programPublicId, classPublicId,
                membershipPublicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
