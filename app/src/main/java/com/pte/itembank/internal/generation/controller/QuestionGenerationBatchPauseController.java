package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.internal.generation.batch.BatchPauseService;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pause and resume of a generation batch, beside its cancel ({@code POST .../cancel}): same roles, owner only (checked
 * in the service), each answering the batch as it is afterwards.
 */
@RestController
@RequestMapping("/api/v1/question-generation/batches")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class QuestionGenerationBatchPauseController {

    private final BatchPauseService pauses;

    public QuestionGenerationBatchPauseController(BatchPauseService pauses) {
        this.pauses = pauses;
    }

    @PostMapping("/{publicId}/pause")
    public ApiResponse<GenerationBatchResponse> pause(@PathVariable UUID publicId) {
        return ApiResponse.success(pauses.pause(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/resume")
    public ApiResponse<GenerationBatchResponse> resume(@PathVariable UUID publicId) {
        return ApiResponse.success(pauses.resume(publicId, CurrentUserContext.required()));
    }
}
