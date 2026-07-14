package com.aptis.modules.iam.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.dto.request.studentimport.ConfirmRequest;
import com.aptis.modules.iam.dto.request.studentimport.PrepareImportRequest;
import com.aptis.modules.iam.dto.request.studentimport.PreviewRequest;
import com.aptis.modules.iam.dto.response.studentimport.ParseFileResponse;
import com.aptis.modules.iam.dto.response.studentimport.PreviewResponse;
import com.aptis.modules.iam.interfaces.StudentImportConfirmer;
import com.aptis.modules.iam.interfaces.StudentImportParser;
import com.aptis.modules.iam.interfaces.StudentImportPreviewer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(IamApiConstants.HOST_STUDENT_IMPORT_BASE)
public class HostImportController {

    private final StudentImportParser importParseService;
    private final StudentImportPreviewer importPreviewService;
    private final StudentImportConfirmer importConfirmService;

    public HostImportController(
            StudentImportParser importParseService,
            StudentImportPreviewer importPreviewService,
            StudentImportConfirmer importConfirmService) {
        this.importParseService = importParseService;
        this.importPreviewService = importPreviewService;
        this.importConfirmService = importConfirmService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping(
            value = IamApiConstants.PATH_IMPORT_PARSE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ParseFileResponse>> parseFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        ParseFileResponse response = importParseService.parseExcelFile(
                file,
                principal.userId().toString(),
                principal.tenantId());

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping(IamApiConstants.PATH_IMPORT_PREPARE)
    public ResponseEntity<ApiResponse<ParseFileResponse>> prepareImport(
            @Valid @RequestBody PrepareImportRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        ParseFileResponse response = importParseService.prepareCleanedRows(
                request,
                principal.userId().toString(),
                principal.tenantId());

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping(IamApiConstants.PATH_IMPORT_PREVIEW)
    public ResponseEntity<ApiResponse<PreviewResponse>> previewImport(
            @Valid @RequestBody PreviewRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        PreviewResponse response = importPreviewService.preview(
                principal.userId().toString(),
                principal.tenantId(),
                request);

        return ResponseEntity.ok(ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping(IamApiConstants.PATH_IMPORT_CONFIRM)
    public ResponseEntity<byte[]> confirmImport(
            @Valid @RequestBody ConfirmRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        byte[] excelPayload = importConfirmService.confirm(
                principal.userId().toString(),
                principal.tenantId(),
                request.importId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(IamApiConstants.EXCEL_CONTENT_TYPE));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("students_credentials_" + request.importId() + ".xlsx")
                .build());

        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(excelPayload);
    }
}
