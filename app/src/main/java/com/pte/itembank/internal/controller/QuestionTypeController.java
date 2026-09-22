package com.pte.itembank.internal.controller;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform question-type catalog used by the question bank and admin UI. */
@RestController
@RequestMapping("/api/v1/question-types")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
public class QuestionTypeController {

    private final QuestionTypeService service;

    public QuestionTypeController(QuestionTypeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<QuestionTypeResponse>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.success(service.list(activeOnly));
    }

    @GetMapping("/supported")
    public ApiResponse<List<SupportedQuestionTypeResponse>> listSupported() {
        return ApiResponse.success(service.listSupported());
    }

    @GetMapping("/{publicId}")
    public ApiResponse<QuestionTypeResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.get(publicId));
    }

    @PostMapping
    public ApiResponse<QuestionTypeResponse> create(
            @Valid @RequestBody CreateQuestionTypeRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<QuestionTypeResponse> update(@PathVariable UUID publicId,
                                                     @Valid @RequestBody UpdateQuestionTypeRequest request) {
        return ApiResponse.success(service.update(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> delete(@PathVariable UUID publicId) {
        service.delete(publicId);
        return ApiResponse.success(null);
    }

}
