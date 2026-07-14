package com.aptis.modules.iam.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.dto.response.admin.HostStudentResponse;
import com.aptis.modules.iam.service.HostStudentService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(IamApiConstants.HOST_STUDENTS_BASE)
public class HostStudentController {

    private final HostStudentService hostStudentService;

    public HostStudentController(HostStudentService hostStudentService) {
        this.hostStudentService = hostStudentService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @GetMapping
    public ResponseEntity<ApiResponse<List<HostStudentResponse>>> listStudents(
            @AuthenticationPrincipal JwtPrincipal principal,
            @ParameterObject @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest servletRequest) {

        Page<HostStudentResponse> students = hostStudentService.listStudents(
                principal.tenantId(), pageable);

        return ResponseEntity.ok(
                ApiResponse.paged(
                        HttpStatus.OK,
                        "SUCCESS",
                        "Students fetched successfully",
                        students,
                        servletRequest.getRequestURI()));
    }
}
