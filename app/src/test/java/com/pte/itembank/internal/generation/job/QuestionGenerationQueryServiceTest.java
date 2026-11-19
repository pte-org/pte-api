package com.pte.itembank.internal.generation.job;

import com.pte.billing.BillingSettingsService;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationQueryServiceTest {

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final QuestionGenerationQueryService service = new QuestionGenerationQueryService(jobs, items, batches,
            mock(GenerationTaskTypeCatalog.class), new GenerationQuotaGuard(jobs, new GenerationLimits(mock(BillingSettingsService.class, call -> call.getArgument(1)), properties),
            Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC)));
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

    private static QuestionGenerationJob child() {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setId(5L);
        job.setPublicId(UUID.randomUUID());
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setBatchId(40L);
        job.setBatchSeq(2);
        job.setBatchRowIndex(0);
        return job;
    }

    private static QuestionGenerationBatch batch() {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(UUID.randomUUID());
        return batch;
    }

    @Test
    void batchChildrenAreHiddenFromTheJobListByDefault() {
        when(jobs.findByBatchIdIsNullAndDeletedFalse(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(jobs.findByRequestedByUserPublicIdAndBatchIdIsNullAndDeletedFalse(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(0, 20, false, admin);
        service.list(0, 20, false, staff);

        verify(jobs).findByBatchIdIsNullAndDeletedFalse(any(Pageable.class));
        verify(jobs).findByRequestedByUserPublicIdAndBatchIdIsNullAndDeletedFalse(eq(staff.userId()),
                any(Pageable.class));
        verify(jobs, never()).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    void askedForChildrenTheListShowsThemWithTheirBatch() {
        QuestionGenerationBatch batch = batch();
        when(jobs.findByDeletedFalse(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(child())));
        when(batches.findAllById(any())).thenReturn(List.of(batch));

        GenerationJobResponse listed = service.list(0, 20, true, admin).data().get(0);

        assertThat(listed.batchPublicId()).isEqualTo(batch.getPublicId());
        assertThat(listed.batchSeq()).isEqualTo(2);
    }

    @Test
    void aChildStaysReadableOnItsOwnWithItsBatch() {
        QuestionGenerationJob child = child();
        child.setRequestedByUserPublicId(admin.userId());
        QuestionGenerationBatch batch = batch();
        when(jobs.findByPublicIdAndDeletedFalse(child.getPublicId())).thenReturn(Optional.of(child));
        when(batches.findById(40L)).thenReturn(Optional.of(batch));

        assertThat(service.get(child.getPublicId(), admin).batchPublicId()).isEqualTo(batch.getPublicId());
    }
}
