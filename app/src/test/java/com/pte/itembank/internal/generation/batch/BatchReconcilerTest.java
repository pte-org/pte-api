package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchReconcilerTest {

    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final BatchChainingService chaining = mock(BatchChainingService.class);
    private final BatchReconciler reconciler = new BatchReconciler(batches, chaining);

    @Test
    void everySuspectBatchIsRepairedOnceInItsOwnCall() {
        when(batches.findOpenIdsWithoutOpenChild()).thenReturn(List.of(1L, 2L));
        when(batches.findOpenIdsWithOnlyPendingChildren()).thenReturn(List.of(2L, 3L));

        reconciler.tick();

        verify(chaining).reconcile(1L);
        verify(chaining, times(1)).reconcile(2L);
        verify(chaining).reconcile(3L);
    }

    @Test
    void healthyBatchesAreLeftAlone() {
        when(batches.findOpenIdsWithoutOpenChild()).thenReturn(List.of());
        when(batches.findOpenIdsWithOnlyPendingChildren()).thenReturn(List.of());

        reconciler.tick();

        verify(chaining, never()).reconcile(anyLong());
    }
}
