package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Safety net for batch chaining. Every child terminal path calls the chaining hook in its own transaction, but a
 * missed path would leave a batch open forever and block its writer (one open unit per writer). This finds open
 * batches without open children, or with only waiting children, and repairs each in its own short transaction.
 */
@Component
@ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
public class BatchReconciler {

    private final GenerationBatchRepository batches;
    private final BatchChainingService chaining;

    public BatchReconciler(GenerationBatchRepository batches, BatchChainingService chaining) {
        this.batches = batches;
        this.chaining = chaining;
    }

    @Scheduled(fixedDelayString = BatchGenerationConstants.RECONCILE_DELAY_PROPERTY,
            initialDelayString = BatchGenerationConstants.RECONCILE_DELAY_PROPERTY)
    public void tick() {
        Set<Long> suspects = new LinkedHashSet<>(batches.findOpenIdsWithoutOpenChild());
        suspects.addAll(batches.findOpenIdsWithOnlyPendingChildren());
        suspects.forEach(chaining::reconcile);
    }
}
