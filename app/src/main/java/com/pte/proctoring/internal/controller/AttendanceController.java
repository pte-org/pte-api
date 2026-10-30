package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.service.AttendanceCoordinatorService;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.dto.response.AttendancePageView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-sessions")
@PreAuthorize("hasAnyRole('HOST_ADMIN','PROCTOR')")
public class AttendanceController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AttendanceCoordinatorService attendanceCoordinatorService;

    public AttendanceController(AttendanceCoordinatorService attendanceCoordinatorService) {
        this.attendanceCoordinatorService = attendanceCoordinatorService;
    }

    /** Host admins see the whole roster; proctors only the sessions they are assigned to (checked in the service). */
    private static boolean isHost(CurrentUser caller) {
        return caller.hasRole(SecurityRoles.HOST_ADMIN);
    }

    @GetMapping("/{sessionPublicId}/attendance")
    public ApiResponse<AttendancePageView> getRoster(
            @PathVariable UUID sessionPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return ApiResponse.success(attendanceCoordinatorService.getAttendanceRoster(
                sessionPublicId, status, search, safePage, safeSize, caller, isHost(caller)));
    }

    @PatchMapping("/{sessionPublicId}/attendance/students/{studentPublicId}")
    public ApiResponse<AttendanceCommandReceiptView> recordAttendance(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @Valid @RequestBody RecordAttendanceRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.recordAttendance(
                sessionPublicId, studentPublicId, request, caller, isHost(caller)));
    }

    @GetMapping("/{sessionPublicId}/attendance/receipts/{requestPublicId}")
    public ApiResponse<AttendanceCommandReceiptView> getReceipt(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID requestPublicId) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.getReceipt(
                sessionPublicId, requestPublicId, caller, isHost(caller)));
    }

    @GetMapping("/{sessionPublicId}/attendance/students/{studentPublicId}/history")
    public ApiResponse<AttendanceHistoryView> getHistory(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return ApiResponse.success(attendanceCoordinatorService.getHistory(
                sessionPublicId, studentPublicId, safePage, safeSize, caller, isHost(caller)));
    }

    @PatchMapping("/{sessionPublicId}/attendance/students/{studentPublicId}/identity")
    public ApiResponse<AttendanceRowView> recordIdentityVerification(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @Valid @RequestBody RecordIdentityVerificationRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.recordIdentityVerification(
                sessionPublicId, studentPublicId, request, caller, isHost(caller)));
    }
}
