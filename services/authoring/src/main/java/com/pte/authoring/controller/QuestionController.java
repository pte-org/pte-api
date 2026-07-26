package com.pte.authoring.controller;

import com.pte.authoring.dto.request.CreateQuestionRequest;
import com.pte.authoring.dto.response.QuestionResponse;
import com.pte.authoring.service.QuestionService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/questions")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR','HOST_ADMIN','HOST_AUTHOR')")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ApiResponse<QuestionResponse> create(@Valid @RequestBody CreateQuestionRequest request) {
        return ApiResponse.success(questionService.create(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<QuestionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(questionService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<QuestionResponse>> list() {
        return ApiResponse.success(questionService.listAccessible(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
