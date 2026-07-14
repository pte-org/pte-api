package com.aptis.modules.examoperations.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.constant.ExamOperationsApiConstants;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.dto.AssignExamRequest;
import com.aptis.modules.examoperations.dto.AssignExamResponse;
import com.aptis.modules.examoperations.dto.ExamResponse;
import com.aptis.modules.examoperations.dto.RosterImportRequest;
import com.aptis.modules.examoperations.dto.RosterImportResponse;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.examoperations.service.RosterImportService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ExamOperationsApiConstants.HOST_IMPORTS)
public class RosterImportController {

    private final RosterImportService rosterImportService;
    private final ExamRepository examRepository;

    public RosterImportController(
            RosterImportService rosterImportService,
            ExamRepository examRepository) {
        this.rosterImportService = rosterImportService;
        this.examRepository = examRepository;
    }

    /**
     * POST /api/v1/host/imports — Provision students from parsed roster data.
     */
    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @PostMapping
    public ResponseEntity<ApiResponse<RosterImportResponse>> provisionStudents(
            @Valid @RequestBody RosterImportRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        String hostId = principal.userId().toString();
        Long organizationId = principal.tenantId();

        RosterImportResponse response = rosterImportService.provisionStudents(
                hostId,
                organizationId,
                request.filename(),
                request.validRows(),
                request.errors());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "SUCCESS",
                        "Students provisioned successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    /**
     * GET /api/v1/host/imports/{batchId} — Retrieve stored import report.
     */
    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @GetMapping(ExamOperationsApiConstants.PATH_BATCH_ID)
    public ResponseEntity<ApiResponse<RosterImportResponse>> getImportReport(
            @PathVariable Long batchId,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        String hostId = principal.userId().toString();
        RosterImportResponse response = rosterImportService.getImportReport(hostId, batchId);

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    /**
     * POST /api/v1/host/imports/{batchId}/assign — Assign exam to batch.
     */
    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @PostMapping(ExamOperationsApiConstants.PATH_BATCH_ID + ExamOperationsApiConstants.PATH_ASSIGN)
    public ResponseEntity<ApiResponse<AssignExamResponse>> assignExamToBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody AssignExamRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        String hostId = principal.userId().toString();
        AssignExamResponse response = rosterImportService.assignExamToBatch(
                hostId, batchId, request.examId());

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    /**
     * GET /api/v1/host/imports/{batchId}/exams — List assignable exams for this host.
     */
    @PreAuthorize(ExamOperationsApiConstants.AUTHORITY_HOST)
    @GetMapping(ExamOperationsApiConstants.PATH_BATCH_ID + ExamOperationsApiConstants.PATH_EXAMS)
    public ResponseEntity<ApiResponse<List<ExamResponse>>> getAssignableExams(
            @PathVariable Long batchId,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        String hostId = principal.userId().toString();
        List<Exam> exams = examRepository.findByHostIdAndIsAssignableTrue(hostId);

        List<ExamResponse> responses = exams.stream()
                .map(exam -> new ExamResponse(
                        exam.getId(),
                        exam.getName(),
                        exam.getCode(),
                        exam.getIsAssignable()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses, servletRequest.getRequestURI()));
    }
}
