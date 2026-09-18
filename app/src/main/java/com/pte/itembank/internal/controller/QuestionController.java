package com.pte.itembank.internal.controller;

import com.pte.itembank.ItembankService;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.request.UpdateQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
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
    public ApiResponse<List<QuestionResponse>> list(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        return ApiResponse.success(itembankService.listAccessible(currentUser(), taskType, section, status, q));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<QuestionResponse> update(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        return ApiResponse.success(itembankService.update(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/edit")
    public ApiResponse<QuestionResponse> createRevision(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.createRevision(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/submit-approval")
    public ApiResponse<QuestionResponse> submitApproval(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.submitApproval(publicId, currentUser()));
    }

    @PostMapping({"/{publicId}/approval", "/{publicId}/approve"})
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<QuestionResponse> approve(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.approve(publicId, currentUser()));
    }

    @PostMapping({"/{publicId}/rejection", "/{publicId}/reject"})
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<QuestionResponse> reject(@PathVariable UUID publicId,
            @Valid @RequestBody RejectQuestionRequest request) {
        return ApiResponse.success(itembankService.reject(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/publish")
    public ApiResponse<QuestionResponse> publish(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.publish(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/archive")
    public ApiResponse<QuestionResponse> archive(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.archive(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/unarchive")
    public ApiResponse<QuestionResponse> unarchive(@PathVariable UUID publicId) {
        return ApiResponse.success(itembankService.unarchive(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
