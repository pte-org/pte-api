package com.pte.itembank.internal.generation.dto;

import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GenerationJobResponse(
        UUID publicId, String taskType, String cefrLevel, String pool, String topicHint, int requestedCount,
        String status, int succeededCount, int failedCount, String failureCode, Instant createdAt,
        Instant startedAt, Instant finishedAt, List<GenerationItemResponse> items) {

    /** Summary without items, for lists. */
    public static GenerationJobResponse from(QuestionGenerationJob job) {
        return from(job, List.of());
    }

    public static GenerationJobResponse from(QuestionGenerationJob job, List<QuestionGenerationJobItem> items) {
        return new GenerationJobResponse(job.getPublicId(), job.getTaskTypeKey(), job.getCefrLevel().name(),
                job.getPool().name(), job.getTopicHint(), job.getRequestedCount(), job.getStatus().name(),
                job.getSucceededCount(), job.getFailedCount(), job.getFailureCode(), job.getCreatedAt(),
                job.getStartedAt(), job.getFinishedAt(),
                items.stream().map(GenerationItemResponse::from).toList());
    }
}
