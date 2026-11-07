package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Read side of generation: the job and its items, the job list, the task types and the caller's quota. */
@Service
public class QuestionGenerationQueryService {

    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationTaskTypeCatalog catalog;
    private final QuestionGenerationProperties properties;
    private final Clock clock;

    public QuestionGenerationQueryService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                                          GenerationTaskTypeCatalog catalog, QuestionGenerationProperties properties,
                                          Clock clock) {
        this.jobs = jobs;
        this.items = items;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse get(UUID publicId, CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN);
        QuestionGenerationJob job = jobs.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationJobNotFoundException::new);
        return GenerationJobResponse.from(job, items.findByJobIdOrderByItemIndexAsc(job.getId()));
    }

    /** Newest first, without items (the list only needs the counters). */
    @Transactional(readOnly = true)
    public PagedResult<GenerationJobResponse> list(int page, int size, CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN);
        int safeSize = Math.min(Math.max(size, 1), QuestionGenerationConstants.MAX_PAGE_SIZE);
        Page<QuestionGenerationJob> result = jobs.findByDeletedFalse(
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<GenerationJobResponse> data = result.getContent().stream().map(GenerationJobResponse::from).toList();
        return new PagedResult<>(data, new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    public List<GenerationTaskTypeResponse> taskTypes(CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN);
        return catalog.supported();
    }

    @Transactional(readOnly = true)
    public GenerationQuotaResponse quota(CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN);
        long used = jobs.sumRequestedSinceByUser(caller.userId(), clock.instant().truncatedTo(ChronoUnit.DAYS));
        return new GenerationQuotaResponse(used, properties.getLimits().getMaxItemsPerUserPerDay());
    }
}
