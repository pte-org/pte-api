package com.aptis.modules.questionbank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.questionbank.constant.QuestionBankApiConstants;
import com.aptis.modules.questionbank.constant.QuestionBankConstants;
import com.aptis.modules.questionbank.dto.response.QuestionImportResultResponse;
import com.aptis.modules.questionbank.service.QuestionImportService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(QuestionBankApiConstants.QUESTIONS)
public class QuestionImportController {

    private final QuestionImportService questionImportService;

    public QuestionImportController(QuestionImportService questionImportService) {
        this.questionImportService = questionImportService;
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_HOST)
    @PostMapping(value = QuestionBankApiConstants.PATH_IMPORT, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuestionImportResultResponse>> importQuestions(
            @RequestPart("file") MultipartFile excelFile,
            @RequestPart(value = "audioZip", required = false) MultipartFile audioZip,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionImportResultResponse response =
                questionImportService.importQuestions(excelFile, audioZip, principal);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        HttpStatus.OK,
                        QuestionBankConstants.OP_QUESTION_IMPORT,
                        QuestionBankConstants.QUESTION_IMPORT_SUCCESS,
                        response,
                        servletRequest.getRequestURI()));
    }
}
