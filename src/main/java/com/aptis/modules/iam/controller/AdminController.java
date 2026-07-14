package com.aptis.modules.iam.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.dto.request.admin.AssignOrgRequest;
import com.aptis.modules.iam.dto.request.admin.CreateGraderRequest;
import com.aptis.modules.iam.dto.request.admin.CreateHostRequest;
import com.aptis.modules.iam.dto.response.admin.GraderResponse;
import com.aptis.modules.iam.dto.response.admin.HostResponse;
import com.aptis.modules.iam.interfaces.GraderAdminOperations;
import com.aptis.modules.iam.interfaces.HostManagement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(IamApiConstants.ADMIN_BASE)
public class AdminController {

    private final HostManagement hostService;
    private final GraderAdminOperations graderAdminService;

    public AdminController(HostManagement hostService, GraderAdminOperations graderAdminService) {
        this.hostService = hostService;
        this.graderAdminService = graderAdminService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @PostMapping(IamApiConstants.PATH_HOSTS)
    public ResponseEntity<ApiResponse<HostResponse>> createHost(
            @Valid @RequestBody CreateHostRequest request,
            HttpServletRequest servletRequest) {
        HostResponse response = hostService.createHost(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @PostMapping(IamApiConstants.PATH_GRADERS)
    public ResponseEntity<ApiResponse<GraderResponse>> createGrader(
            @Valid @RequestBody CreateGraderRequest request,
            HttpServletRequest servletRequest) {
        GraderResponse response = graderAdminService.createGrader(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @GetMapping(IamApiConstants.PATH_GRADERS)
    public ResponseEntity<ApiResponse<List<GraderResponse>>> listGraders(HttpServletRequest servletRequest) {
        List<GraderResponse> responses = graderAdminService.listGraders();
        return ResponseEntity.ok(ApiResponse.success(responses, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @PostMapping(IamApiConstants.PATH_GRADER_ORGS)
    public ResponseEntity<ApiResponse<Void>> assignOrg(
            @PathVariable Long graderId,
            @Valid @RequestBody AssignOrgRequest request,
            HttpServletRequest servletRequest) {
        graderAdminService.assignOrgToGrader(graderId, request.getOrganizationId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(null, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @DeleteMapping(IamApiConstants.PATH_GRADER_ORG_REVOKE)
    public ResponseEntity<ApiResponse<Void>> revokeOrg(
            @PathVariable Long graderId,
            @PathVariable Long orgId,
            HttpServletRequest servletRequest) {
        graderAdminService.revokeOrgFromGrader(graderId, orgId);
        return ResponseEntity.ok(ApiResponse.success(null, servletRequest.getRequestURI()));
    }
}
