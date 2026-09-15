package com.pte.itembank.internal.controller;

import com.pte.itembank.ItembankService;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
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

    private final ItembankService itembankService;

    public QuestionController(ItembankService itembankService) {
        this.itembankService = itembankService;
    }

    @PostMapping
    public ApiResponse<QuestionResponse> create(@Valid @RequestBody CreateQuestionRequest request) {
        return ApiResponse.success(itembankService.create(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<QuestionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<QuestionResponse>> list() {
        return ApiResponse.success(itembankService.listAccessible(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
