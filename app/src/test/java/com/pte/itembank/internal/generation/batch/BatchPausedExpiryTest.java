package com.pte.itembank.internal.generation.batch;

import com.pte.billing.BillingSettingsService;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchPausedExpiryTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final long BATCH_ID = 9L;

    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final QuestionGenerationBatchService batchService = mock(QuestionGenerationBatchService.class);
    private final BillingSettingsService settings = mock(BillingSettingsService.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final AuditLogService audit = mock(AuditLogService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final BatchPausedExpiry expiry = new BatchPausedExpiry(batches, batchService,
            new GenerationLimits(settings, properties), audit, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
    private final QuestionGenerationBatch batch = new QuestionGenerationBatch();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The admin setting answers whatever the test put in the deployment value.
        when(settings.questionGenerationLimit(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> call.getArgument(1));
        doAnswer(call -> {
            ((Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0)).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        batch.setId(BATCH_ID);
        batch.setPublicId(UUID.randomUUID());
        batch.setStatus(BatchStatus.PAUSED);
        when(batches.findByIdForUpdate(BATCH_ID)).thenReturn(Optional.of(batch));
        when(batches.findById(BATCH_ID)).thenReturn(Optional.of(batch));
    }

    @Test
    void aBatchPausedLongerThanTheExpiryIsCancelledByTheSystem() {
        properties.getLimits().setPausedBatchExpiryHours(24);
        batch.setPausedAt(NOW.minus(Duration.ofHours(25)));
        when(batches.findPausedBefore(NOW.minus(Duration.ofHours(24)))).thenReturn(List.of(BATCH_ID));
        when(batchService.cancelLocked(BATCH_ID, BatchGenerationConstants.GENERATION_BATCH_PAUSE_EXPIRED))
                .thenReturn(true);

        expiry.tick();

        ArgumentCaptor<CurrentUser> actor = ArgumentCaptor.forClass(CurrentUser.class);
        verify(audit).record(actor.capture(), eq(BatchGenerationConstants.BATCH_AGGREGATE),
                eq(batch.getPublicId().toString()), eq(BatchGenerationConstants.BATCH_EXPIRED_WHILE_PAUSED),
                anyString());
        assertThat(actor.getValue().userId()).isEqualTo(BatchGenerationConstants.SYSTEM_ACTOR_USER_ID);
    }

    @Test
    void anExpiryOfZeroKeepsPausedBatches() {
        properties.getLimits().setPausedBatchExpiryHours(0);

        expiry.tick();

        verify(batches, never()).findPausedBefore(any());
        verify(batchService, never()).cancelLocked(anyLong(), any());
    }

    @Test
    void aBatchResumedSinceTheSelectIsLeftAlone() {
        properties.getLimits().setPausedBatchExpiryHours(2);
        batch.setStatus(BatchStatus.RUNNING);
        when(batches.findPausedBefore(NOW.minus(Duration.ofHours(2)))).thenReturn(List.of(BATCH_ID));

        expiry.tick();

        verify(batchService, never()).cancelLocked(anyLong(), any());
    }

    @Test
    void aBatchPausedForLessThanTheExpiryIsKeptEvenIfTheSelectWasStale() {
        properties.getLimits().setPausedBatchExpiryHours(24);
        batch.setPausedAt(NOW.minus(Duration.ofHours(23)));
        when(batches.findPausedBefore(NOW.minus(Duration.ofHours(24)))).thenReturn(List.of(BATCH_ID));

        expiry.tick();

        verify(batchService, never()).cancelLocked(anyLong(), any());
    }

    @Test
    void oneFailingBatchDoesNotStopTheSweep() {
        properties.getLimits().setPausedBatchExpiryHours(24);
        when(batches.findPausedBefore(any())).thenReturn(List.of(BATCH_ID, BATCH_ID + 1));
        when(batches.findByIdForUpdate(BATCH_ID)).thenThrow(new IllegalStateException("boom"));
        QuestionGenerationBatch other = new QuestionGenerationBatch();
        other.setId(BATCH_ID + 1);
        other.setPublicId(UUID.randomUUID());
        other.setStatus(BatchStatus.PAUSED);
        other.setPausedAt(NOW.minus(Duration.ofHours(30)));
        when(batches.findByIdForUpdate(BATCH_ID + 1)).thenReturn(Optional.of(other));

        expiry.tick();

        verify(batchService).cancelLocked(BATCH_ID + 1, BatchGenerationConstants.GENERATION_BATCH_PAUSE_EXPIRED);
    }
}
