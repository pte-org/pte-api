package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.service.AttendanceCoordinatorService;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.dto.response.AttendancePageView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
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
@RequestMapping("/api/v1/host/exam-sessions")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostAttendanceController {

    private final AttendanceCoordinatorService attendanceCoordinatorService;

    public HostAttendanceController(AttendanceCoordinatorService attendanceCoordinatorService) {
        this.attendanceCoordinatorService = attendanceCoordinatorService;
    }

    @GetMapping("/{sessionPublicId}/attendance")
    public ApiResponse<AttendancePageView> getRoster(
            @PathVariable UUID sessionPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.getAttendanceRoster(
                sessionPublicId, status, search, page, size, caller, true));
    }

    @PatchMapping("/{sessionPublicId}/attendance/students/{studentPublicId}")
    public ApiResponse<AttendanceCommandReceiptView> recordAttendance(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @Valid @RequestBody RecordAttendanceRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.recordAttendance(
                sessionPublicId, studentPublicId, request, caller, true));
    }

    @GetMapping("/{sessionPublicId}/attendance/receipts/{requestPublicId}")
    public ApiResponse<AttendanceCommandReceiptView> getReceipt(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID requestPublicId) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.getReceipt(
                sessionPublicId, requestPublicId, caller, true));
    }

    @GetMapping("/{sessionPublicId}/attendance/students/{studentPublicId}/history")
    public ApiResponse<AttendanceHistoryView> getHistory(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(attendanceCoordinatorService.getHistory(
                sessionPublicId, studentPublicId, page, size, caller, true));
    }
}
