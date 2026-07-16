package com.aptis.modules.examoperations.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.constant.ExamOperationsApiConstants;
import com.aptis.modules.examoperations.dto.AssignProctorRequest;
import com.aptis.modules.examoperations.dto.CreateExamRequest;
import com.aptis.modules.examoperations.dto.ExamDetailResponse;
import com.aptis.modules.examoperations.dto.ExamSettingsRequest;
import com.aptis.modules.examoperations.service.ExamService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ExamOperationsApiConstants.EXAMS)
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @PostMapping
    public ResponseEntity<ApiResponse<ExamDetailResponse>> createExam(
            @Valid @RequestBody CreateExamRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ExamDetailResponse response = examService.createExam(request, principal);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "SUCCESS",
                        "Exam created successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @GetMapping(ExamOperationsApiConstants.PATH_EXAM_ID)
    public ResponseEntity<ApiResponse<ExamDetailResponse>> getExam(
            @PathVariable Long examId,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ExamDetailResponse response = examService.getExam(examId, principal);

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @PutMapping(ExamOperationsApiConstants.PATH_SETTINGS)
    public ResponseEntity<ApiResponse<ExamDetailResponse>> updateSettings(
            @PathVariable Long examId,
            @Valid @RequestBody ExamSettingsRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ExamDetailResponse response = examService.updateSettings(examId, request, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "SUCCESS",
                        "Exam settings updated successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @PutMapping(ExamOperationsApiConstants.PATH_PROCTOR)
    public ResponseEntity<ApiResponse<ExamDetailResponse>> assignProctor(
            @PathVariable Long examId,
            @Valid @RequestBody AssignProctorRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ExamDetailResponse response = examService.assignProctor(examId, request.proctorId(), principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "SUCCESS",
                        "Proctor assigned successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @DeleteMapping(ExamOperationsApiConstants.PATH_PROCTOR)
    public ResponseEntity<ApiResponse<ExamDetailResponse>> unassignProctor(
            @PathVariable Long examId,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ExamDetailResponse response = examService.unassignProctor(examId, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        "SUCCESS",
                        "Proctor unassigned successfully",
                        response,
                        servletRequest.getRequestURI()));
    }
}
