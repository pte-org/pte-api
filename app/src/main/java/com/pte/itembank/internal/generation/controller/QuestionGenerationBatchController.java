package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.internal.generation.batch.BatchDraftDeletionService;
import com.pte.itembank.internal.generation.batch.BatchQuestionsQueryService;
import com.pte.itembank.internal.generation.batch.QuestionGenerationBatchQueryService;
import com.pte.itembank.internal.generation.batch.QuestionGenerationBatchService;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteRequest;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteResponse;
import com.pte.itembank.internal.generation.batch.dto.BatchQuestionResponse;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.batch.dto.SubmitGenerationBatchRequest;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Batch AI question generation: many questions in one request, run as child jobs one after another. Same roles as
 * single-job generation; the feature flag, ownership and every rule live in the services.
 */
@RestController
@RequestMapping("/api/v1/question-generation/batches")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class QuestionGenerationBatchController {

    private final QuestionGenerationBatchService batchService;
    private final QuestionGenerationBatchQueryService queryService;
    private final BatchDraftDeletionService draftDeletion;
    private final BatchQuestionsQueryService questionsQuery;

    public QuestionGenerationBatchController(QuestionGenerationBatchService batchService,
                                             QuestionGenerationBatchQueryService queryService,
                                             BatchDraftDeletionService draftDeletion,
                                             BatchQuestionsQueryService questionsQuery) {
        this.batchService = batchService;
        this.queryService = queryService;
        this.draftDeletion = draftDeletion;
        this.questionsQuery = questionsQuery;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GenerationBatchResponse>> submit(
            @Valid @RequestBody SubmitGenerationBatchRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(batchService.submit(request, currentUser())));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<GenerationBatchResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(queryService.get(publicId, currentUser()));
    }

    /** Every question slot of the batch, with a preview of each saved question, for its result cards. */
    @GetMapping("/{publicId}/questions")
    public ApiResponse<List<BatchQuestionResponse>> questions(@PathVariable UUID publicId) {
        return ApiResponse.success(questionsQuery.list(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<PagedResult<GenerationBatchResponse>> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean mine) {
        return ApiResponse.success(queryService.list(page, size, mine, currentUser()));
    }

    @PostMapping("/{publicId}/cancel")
    public ApiResponse<GenerationBatchResponse> cancel(@PathVariable UUID publicId) {
        return ApiResponse.success(batchService.cancel(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/drafts/delete")
    public ApiResponse<BatchDraftDeleteResponse> deleteDrafts(@PathVariable UUID publicId,
            @Valid @RequestBody(required = false) BatchDraftDeleteRequest request) {
        BatchDraftDeleteRequest body = request == null ? new BatchDraftDeleteRequest(null) : request;
        return ApiResponse.success(draftDeletion.deleteDrafts(publicId, body, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
