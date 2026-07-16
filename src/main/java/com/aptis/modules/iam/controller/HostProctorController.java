package com.aptis.modules.iam.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.dto.request.host.CreateProctorRequest;
import com.aptis.modules.iam.dto.response.host.ProctorResponse;
import com.aptis.modules.iam.interfaces.HostProctorOperations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(IamApiConstants.HOST_PROCTORS_BASE)
public class HostProctorController {

    private final HostProctorOperations hostProctorService;

    public HostProctorController(HostProctorOperations hostProctorService) {
        this.hostProctorService = hostProctorService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping
    public ResponseEntity<ApiResponse<ProctorResponse>> createProctor(
            @Valid @RequestBody CreateProctorRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        ProctorResponse response = hostProctorService.createProctor(
                principal.tenantId(), principal.userId().toString(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        IamApiConstants.OP_HOST_CREATE_PROCTOR,
                        "Proctor created successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProctorResponse>>> listProctors(
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        List<ProctorResponse> responses = hostProctorService.listProctors(principal.tenantId());
        return ResponseEntity.ok(ApiResponse.success(responses, servletRequest.getRequestURI()));
    }
}
