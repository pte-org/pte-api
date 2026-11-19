package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;

import java.time.Instant;
import java.util.UUID;

/** One child job of a batch, for the batch detail; the job itself stays readable at {@code /jobs/{jobPublicId}}. */
public record GenerationBatchChildResponse(
        UUID jobPublicId, int batchSeq, int rowIndex, String taskType, String topicHint, int requestedCount,
        String status, int succeededCount, int failedCount, String failureCode, Instant startedAt,
        Instant finishedAt) {

    public static GenerationBatchChildResponse from(QuestionGenerationJob job) {
        return new GenerationBatchChildResponse(job.getPublicId(), job.getBatchSeq(), job.getBatchRowIndex(),
                job.getTaskTypeKey(), job.getTopicHint(), job.getRequestedCount(), job.getStatus().name(),
                job.getSucceededCount(), job.getFailedCount(), job.getFailureCode(), job.getStartedAt(),
                job.getFinishedAt());
    }
}
