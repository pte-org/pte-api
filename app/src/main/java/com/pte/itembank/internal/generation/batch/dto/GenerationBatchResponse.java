package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A batch with its progress. In the detail the counters come from the items of every child (live); in the list they
 * are the stored counters, refreshed whenever a child finishes. {@code children} is empty in the list.
 */
public record GenerationBatchResponse(
        UUID publicId, String pool, String status, int totalRequested, int succeededCount, int failedCount,
        int cancelledCount, int openCount, boolean cancelRequested, String failureCode, UUID currentJobPublicId,
        Instant createdAt, Instant startedAt, Instant finishedAt, List<BatchRow> rows,
        List<GenerationBatchChildResponse> children) {

    /** List entry: stored counters, no children. */
    public static GenerationBatchResponse summary(QuestionGenerationBatch batch) {
        return of(batch, batch.getSucceededCount(), batch.getFailedCount(), batch.getCancelledCount(), null,
                List.of());
    }

    /** Detail: counters from the item stages of all children ({@code stageCounts}), children in run order. */
    public static GenerationBatchResponse detail(QuestionGenerationBatch batch, List<QuestionGenerationJob> children,
                                                 Map<ItemStage, Long> stageCounts) {
        int succeeded = count(stageCounts, ItemStage.READY) + count(stageCounts, ItemStage.SAVED);
        List<QuestionGenerationJob> ordered = children.stream()
                .sorted(Comparator.comparing(QuestionGenerationJob::getBatchSeq)).toList();
        return of(batch, succeeded, count(stageCounts, ItemStage.FAILED), count(stageCounts, ItemStage.CANCELLED),
                currentJob(ordered), ordered.stream().map(GenerationBatchChildResponse::from).toList());
    }

    private static GenerationBatchResponse of(QuestionGenerationBatch batch, int succeeded, int failed, int cancelled,
                                              UUID current, List<GenerationBatchChildResponse> children) {
        int open = Math.max(0, batch.getTotalRequested() - succeeded - failed - cancelled);
        return new GenerationBatchResponse(batch.getPublicId(), batch.getPool().name(), batch.getStatus().name(),
                batch.getTotalRequested(), succeeded, failed, cancelled, open, batch.isCancelRequested(),
                batch.getFailureCode(), current, batch.getCreatedAt(), batch.getStartedAt(), batch.getFinishedAt(),
                batch.getRows(), children);
    }

    /** The child a live view should follow: the one a worker holds, else the one waiting to be claimed. */
    private static UUID currentJob(List<QuestionGenerationJob> children) {
        return children.stream().filter(child -> JobStatus.LEASED.contains(child.getStatus())).findFirst()
                .or(() -> children.stream().filter(child -> child.getStatus() == JobStatus.QUEUED).findFirst())
                .map(QuestionGenerationJob::getPublicId).orElse(null);
    }

    private static int count(Map<ItemStage, Long> stageCounts, ItemStage stage) {
        return stageCounts.getOrDefault(stage, 0L).intValue();
    }
}
