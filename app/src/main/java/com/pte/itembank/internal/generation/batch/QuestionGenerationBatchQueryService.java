package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.exception.GenerationBatchNotFoundException;
import com.pte.itembank.internal.generation.job.GenerationAccessPolicy;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read side of batches. Staff reach only their own batches; reviewers (admin, manager) see every writer's. */
@Service
public class QuestionGenerationBatchQueryService {

    private final GenerationBatchRepository batches;
    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;

    public QuestionGenerationBatchQueryService(GenerationBatchRepository batches, GenerationJobRepository jobs,
                                               GenerationJobItemRepository items) {
        this.batches = batches;
        this.jobs = jobs;
        this.items = items;
    }

    /** The batch the caller may reach, or 404 (also for another writer's batch). */
    @Transactional(readOnly = true)
    public QuestionGenerationBatch require(UUID publicId, CurrentUser caller) {
        QuestionGenerationBatch batch = batches.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationBatchNotFoundException::new);
        GenerationAccessPolicy.requireAccess(caller, batch);
        return batch;
    }

    @Transactional(readOnly = true)
    public GenerationBatchResponse get(UUID publicId, CurrentUser caller) {
        return detail(require(publicId, caller).getId());
    }

    /** Batch, children and one grouped item count: three queries whatever the batch size. */
    @Transactional(readOnly = true)
    public GenerationBatchResponse detail(Long batchId) {
        QuestionGenerationBatch batch = batches.findById(batchId).orElseThrow(GenerationBatchNotFoundException::new);
        Map<ItemStage, Long> stages = new EnumMap<>(ItemStage.class);
        for (Object[] row : items.countStagesByBatch(batchId)) {
            stages.put((ItemStage) row[0], ((Number) row[1]).longValue());
        }
        return GenerationBatchResponse.detail(batch, jobs.findByBatchIdOrderByBatchSeqAsc(batchId), stages);
    }

    /** Newest first, without children. {@code mine} narrows a reviewer's list; staff always get only their own. */
    @Transactional(readOnly = true)
    public PagedResult<GenerationBatchResponse> list(int page, int size, boolean mine, CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        int safeSize = Math.min(Math.max(size, 1), QuestionGenerationConstants.MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QuestionGenerationBatch> result = GenerationAccessPolicy.seesAllJobs(caller) && !mine
                ? batches.findByDeletedFalse(pageRequest)
                : batches.findByRequestedByUserPublicIdAndDeletedFalse(caller.userId(), pageRequest);
        List<GenerationBatchResponse> data = result.getContent().stream().map(GenerationBatchResponse::summary)
                .toList();
        return new PagedResult<>(data, new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }
}
