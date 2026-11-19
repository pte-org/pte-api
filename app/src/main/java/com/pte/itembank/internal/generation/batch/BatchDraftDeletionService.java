package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteRequest;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteResponse;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteResponse.Item;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Soft-deletes the drafts a batch produced, reusing the single-draft rules ({@code QuestionDeletionService}: role,
 * author, draft-only, media cleanup, audit). Deliberately NOT one transaction: each question is deleted in its own
 * (see {@link BatchDraftDeleteExecutor}) and its outcome reported, so one refused question never undoes the others.
 */
@Service
public class BatchDraftDeletionService {

    private final QuestionGenerationBatchQueryService batchQueries;
    private final GenerationJobItemRepository items;
    private final QuestionRepository questions;
    private final BatchDraftDeleteExecutor executor;
    private final AuditLogService audit;

    public BatchDraftDeletionService(QuestionGenerationBatchQueryService batchQueries,
                                     GenerationJobItemRepository items, QuestionRepository questions,
                                     BatchDraftDeleteExecutor executor, AuditLogService audit) {
        this.batchQueries = batchQueries;
        this.items = items;
        this.questions = questions;
        this.executor = executor;
        this.audit = audit;
    }

    public BatchDraftDeleteResponse deleteDrafts(UUID batchPublicId, BatchDraftDeleteRequest request,
                                                 CurrentUser caller) {
        Long batchId = batchQueries.require(batchPublicId, caller).getId();
        Set<UUID> inBatch = new HashSet<>(items.findSavedQuestionPublicIdsByBatch(batchId));
        List<UUID> targets = request.idsOrEmpty().isEmpty()
                ? drafts(inBatch).stream().limit(BatchGenerationConstants.MAX_BULK_DELETE_ITEMS).toList()
                : request.idsOrEmpty();
        List<Item> results = new ArrayList<>();
        for (UUID id : targets) {
            results.add(inBatch.contains(id) ? deleteOne(id, caller)
                    : new Item(id, BatchGenerationConstants.RESULT_NOT_IN_BATCH, null));
        }
        int deleted = (int) results.stream()
                .filter(item -> BatchGenerationConstants.RESULT_DELETED.equals(item.result())).count();
        int remaining = drafts(inBatch).size();
        audit.record(caller, BatchGenerationConstants.BATCH_AGGREGATE, batchPublicId.toString(),
                BatchGenerationConstants.BATCH_DRAFTS_DELETED, String.format(
                        BatchGenerationConstants.BATCH_DRAFTS_DELETED_SUMMARY, results.size(), deleted, remaining));
        return new BatchDraftDeleteResponse(results.size(), deleted, remaining, results);
    }

    private List<UUID> drafts(Set<UUID> inBatch) {
        return inBatch.isEmpty() ? List.of() : questions.findDraftPublicIdsIn(inBatch);
    }

    /** The expected refusals become per-question results; anything else is a real error and stops the call. */
    private Item deleteOne(UUID id, CurrentUser caller) {
        try {
            executor.delete(id, caller);
            return new Item(id, BatchGenerationConstants.RESULT_DELETED, null);
        } catch (QuestionDeletionException notDraft) {
            return new Item(id, BatchGenerationConstants.RESULT_SKIPPED_NOT_DRAFT, notDraft.getCode());
        } catch (AccessDeniedException denied) {
            return new Item(id, BatchGenerationConstants.RESULT_FORBIDDEN, SharedConstants.ACCESS_DENIED);
        } catch (QuestionNotFoundException missing) {
            return new Item(id, BatchGenerationConstants.RESULT_NOT_FOUND, missing.getCode());
        }
    }
}
