package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cancels the batches paused longer than the expiry a platform admin set (0 keeps them). Same path as a person's
 * cancel, by the system actor, each batch in its own transaction so one failure does not stop the sweep. Questions
 * already saved stay; the children that never ran drop out of the daily quota on their own.
 */
@Component
@ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
public class BatchPausedExpiry {

    private static final Logger LOG = LoggerFactory.getLogger(BatchPausedExpiry.class);
    private static final CurrentUser SYSTEM = new CurrentUser(BatchGenerationConstants.SYSTEM_ACTOR_USER_ID, null,
            List.of(BatchGenerationConstants.SYSTEM_ROLE));

    private final GenerationBatchRepository batches;
    private final QuestionGenerationBatchService batchService;
    private final GenerationLimits limits;
    private final AuditLogService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public BatchPausedExpiry(GenerationBatchRepository batches, QuestionGenerationBatchService batchService,
                             GenerationLimits limits, AuditLogService audit, TransactionTemplate transactions,
                             Clock clock) {
        this.batches = batches;
        this.batchService = batchService;
        this.limits = limits;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = BatchGenerationConstants.RECONCILE_DELAY_PROPERTY,
            initialDelayString = BatchGenerationConstants.RECONCILE_DELAY_PROPERTY)
    public void tick() {
        int hours = limits.pausedBatchExpiryHours();
        if (GenerationLimits.isUnlimited(hours)) {
            return;
        }
        Instant cutoff = clock.instant().minus(Duration.ofHours(hours));
        for (Long batchId : batches.findPausedBefore(cutoff)) {
            try {
                transactions.executeWithoutResult(status -> expire(batchId, cutoff, hours));
            } catch (RuntimeException failure) {
                LOG.warn("Paused batch expiry failed: batchId={}, errorType={}", batchId,
                        failure.getClass().getSimpleName());
            }
        }
    }

    /** Checked again under the row lock: a batch resumed since the select is left alone. */
    private void expire(long batchId, Instant cutoff, int hours) {
        QuestionGenerationBatch batch = batches.findByIdForUpdate(batchId).orElse(null);
        if (batch == null || batch.getStatus() != BatchStatus.PAUSED || batch.getPausedAt() == null
                || !batch.getPausedAt().isBefore(cutoff)) {
            return;
        }
        if (batchService.cancelLocked(batchId, BatchGenerationConstants.GENERATION_BATCH_PAUSE_EXPIRED)) {
            BatchStatus closed = batches.findById(batchId).orElseThrow().getStatus();
            audit.record(SYSTEM, BatchGenerationConstants.BATCH_AGGREGATE, batch.getPublicId().toString(),
                    BatchGenerationConstants.BATCH_EXPIRED_WHILE_PAUSED,
                    String.format(BatchGenerationConstants.BATCH_EXPIRED_SUMMARY, closed, hours));
        }
    }
}
