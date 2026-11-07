package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.internal.generation.dto.GenerationJobAction;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.GenerationJobStatusRequest;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.dto.SubmitGenerationJobRequest;
import com.pte.itembank.internal.generation.job.QuestionGenerationJobService;
import com.pte.itembank.internal.generation.job.QuestionGenerationQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** AI question generation for the platform bank. Platform admins only; all rules live in the services. */
@RestController
@RequestMapping("/api/v1/question-generation")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class QuestionGenerationController {

    private final QuestionGenerationJobService jobService;
    private final QuestionGenerationQueryService queryService;

    public QuestionGenerationController(QuestionGenerationJobService jobService,
                                        QuestionGenerationQueryService queryService) {
        this.jobService = jobService;
        this.queryService = queryService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> submit(
            @Valid @RequestBody SubmitGenerationJobRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(jobService.submit(request, currentUser())));
    }

    @GetMapping("/jobs/{publicId}")
    public ApiResponse<GenerationJobResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(queryService.get(publicId, currentUser()));
    }

    @GetMapping("/jobs")
    public ApiResponse<PagedResult<GenerationJobResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(queryService.list(page, size, currentUser()));
    }

    @PatchMapping("/jobs/{publicId}/status")
    public ApiResponse<GenerationJobResponse> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody GenerationJobStatusRequest request) {
        if (request.action() == GenerationJobAction.CANCEL) {
            return ApiResponse.success(jobService.cancel(publicId, currentUser()));
        }
        throw new IllegalStateException(String.valueOf(request.action()));
    }

    @GetMapping("/task-types")
    public ApiResponse<List<GenerationTaskTypeResponse>> taskTypes() {
        return ApiResponse.success(queryService.taskTypes(currentUser()));
    }

    @GetMapping("/quota")
    public ApiResponse<GenerationQuotaResponse> quota() {
        return ApiResponse.success(queryService.quota(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
