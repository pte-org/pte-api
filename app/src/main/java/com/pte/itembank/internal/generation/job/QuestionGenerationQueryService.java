package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read side of generation: the job and its items, the job list, the task types and the caller's quota. */
@Service
public class QuestionGenerationQueryService {

    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationBatchRepository batches;
    private final GenerationTaskTypeCatalog catalog;
    private final GenerationQuotaGuard quota;

    public QuestionGenerationQueryService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                                          GenerationBatchRepository batches, GenerationTaskTypeCatalog catalog,
                                          GenerationQuotaGuard quota) {
        this.quota = quota;
        this.batches = batches;
        this.jobs = jobs;
        this.items = items;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse get(UUID publicId, CurrentUser caller) {
        QuestionGenerationJob job = jobs.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationJobNotFoundException::new);
        GenerationAccessPolicy.requireAccess(caller, job);
        UUID batchPublicId = job.getBatchId() == null ? null
                : batches.findById(job.getBatchId()).map(QuestionGenerationBatch::getPublicId).orElse(null);
        return GenerationJobResponse.from(job, items.findByJobIdOrderByItemIndexAsc(job.getId()), batchPublicId);
    }

    /**
     * Newest first, without items (the list only needs the counters); a staff writer sees only their own jobs. Batch
     * children are shown inside their batch, so they are left out unless {@code includeBatchChildren}.
     */
    @Transactional(readOnly = true)
    public PagedResult<GenerationJobResponse> list(int page, int size, boolean includeBatchChildren,
                                                   CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        int safeSize = Math.min(Math.max(size, 1), QuestionGenerationConstants.MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QuestionGenerationJob> result = page(pageRequest, includeBatchChildren, caller);
        Map<Long, UUID> batchIds = new HashMap<>();
        Set<Long> owners = result.getContent().stream().map(QuestionGenerationJob::getBatchId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!owners.isEmpty()) {
            batches.findAllById(owners).forEach(batch -> batchIds.put(batch.getId(), batch.getPublicId()));
        }
        List<GenerationJobResponse> data = result.getContent().stream()
                .map(job -> GenerationJobResponse.from(job, batchIds.get(job.getBatchId()))).toList();
        return new PagedResult<>(data, new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    private Page<QuestionGenerationJob> page(PageRequest pageRequest, boolean includeBatchChildren,
                                             CurrentUser caller) {
        boolean all = GenerationAccessPolicy.seesAllJobs(caller);
        if (includeBatchChildren) {
            return all ? jobs.findByDeletedFalse(pageRequest)
                    : jobs.findByRequestedByUserPublicIdAndDeletedFalse(caller.userId(), pageRequest);
        }
        return all ? jobs.findByBatchIdIsNullAndDeletedFalse(pageRequest)
                : jobs.findByRequestedByUserPublicIdAndBatchIdIsNullAndDeletedFalse(caller.userId(), pageRequest);
    }

    public List<GenerationTaskTypeResponse> taskTypes(CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        return catalog.supported();
    }

    @Transactional(readOnly = true)
    public GenerationQuotaResponse quota(CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        return quota.snapshot(caller);
    }
}
