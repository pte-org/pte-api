package com.aptis.modules.questionbank.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.questionbank.constant.QuestionBankApiConstants;
import com.aptis.modules.questionbank.constant.QuestionBankConstants;
import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.dto.response.QuestionResponse;
import com.aptis.modules.questionbank.service.QuestionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(QuestionBankApiConstants.QUESTIONS)
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionResponse response = questionService.createQuestion(request, principal);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        QuestionBankConstants.OP_QUESTION_CREATE,
                        QuestionBankConstants.QUESTION_CREATED,
                        response,
                        servletRequest.getRequestURI()));
    }

    // createQuestionWithAsset has been removed in favor of the Two-Step Architecture.
    // Frontend should now call POST /api/v1/assets/upload first, then POST /api/v1/questions

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @GetMapping(QuestionBankApiConstants.PATH_BY_ID)
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionResponse response = questionService.getQuestion(id, principal);

        return ResponseEntity.ok(
                ApiResponse.success(response, servletRequest.getRequestURI()));
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @PutMapping(QuestionBankApiConstants.PATH_BY_ID)
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionResponse response = questionService.updateQuestion(id, request, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        QuestionBankConstants.OP_QUESTION_UPDATE,
                        QuestionBankConstants.QUESTION_UPDATED,
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @DeleteMapping(QuestionBankApiConstants.PATH_BY_ID)
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        questionService.deleteQuestion(id, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        QuestionBankConstants.OP_QUESTION_DELETE,
                        QuestionBankConstants.QUESTION_DELETED,
                        null,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @PostMapping(QuestionBankApiConstants.PATH_PUBLISH)
    public ResponseEntity<ApiResponse<QuestionResponse>> publishQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionResponse response = questionService.publishQuestion(id, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        QuestionBankConstants.OP_QUESTION_PUBLISH,
                        QuestionBankConstants.QUESTION_PUBLISHED,
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @PostMapping(value = QuestionBankApiConstants.PATH_AUDIO, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuestionResponse>> uploadAudio(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        QuestionResponse response = questionService.uploadAudio(id, file, principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK,
                        QuestionBankConstants.OP_QUESTION_AUDIO_UPLOAD,
                        QuestionBankConstants.QUESTION_AUDIO_UPLOADED,
                        response,
                        servletRequest.getRequestURI()));
    }

    @PreAuthorize(QuestionBankApiConstants.AUTHORITY_ADMIN_OR_HOST)
    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> listQuestions(
            @RequestParam(required = false) Skill skill,
            @RequestParam(required = false) Integer part,
            @RequestParam(required = false) QuestionType questionType,
            @RequestParam(required = false) DifficultyLevel difficultyLevel,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) Boolean isCurrent,
            @AuthenticationPrincipal JwtPrincipal principal,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest servletRequest) {

        Page<QuestionResponse> pageResponse = questionService.listQuestions(
                skill, part, questionType, difficultyLevel, status, isCurrent, principal, pageable);

        return ResponseEntity.ok(
                ApiResponse.paged(
                        HttpStatus.OK,
                        "SUCCESS",
                        QuestionBankConstants.QUESTION_LIST_SUCCESS,
                        pageResponse,
                        servletRequest.getRequestURI()));
    }
}
