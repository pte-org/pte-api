package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.pipeline.GenerationItemStateService;
import com.pte.itembank.internal.generation.pipeline.GenerationPipeline;
import com.pte.itembank.internal.generation.pipeline.ItemOutcome;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationJobRunnerTest {

    private final JobLeaseService leases = mock(JobLeaseService.class);
    private final GenerationItemStateService itemState = mock(GenerationItemStateService.class);
    private final GenerationPipeline pipeline = mock(GenerationPipeline.class);
    private final GenerationCallerResolver callers = mock(GenerationCallerResolver.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final GenerationJobRunner runner = new GenerationJobRunner(leases, itemState, pipeline, callers,
            properties);
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "MC_READING_SINGLE",
            CefrLevel.B2, QuestionPool.PRACTICE, null, "worker-1");
    private final CurrentUser admin = new CurrentUser(claim.requesterId(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        when(callers.resolve(claim.requesterId())).thenReturn(Optional.of(admin));
        when(leases.leaseState(claim)).thenReturn(LeaseState.HELD);
        when(leases.renewLease(claim)).thenReturn(true);
        when(itemState.pendingIndexes(5L)).thenReturn(List.of(0, 1, 2));
        when(pipeline.runItem(eq(claim), eq(admin), anyInt(), any())).thenReturn(ItemOutcome.SAVED);
    }

    @Test
    void anAdminWhoLostThePermissionStopsTheJobBeforeAnyCall() {
        when(callers.resolve(claim.requesterId())).thenReturn(Optional.empty());

        runner.run(claim);

        verify(itemState).cancelOpen(claim);
        verify(leases).finish(claim, JobStatus.CANCELLED, "GENERATION_REQUESTER_NOT_ALLOWED");
        verifyNoInteractions(pipeline);
    }

    @Test
    void everyPendingItemIsRunInOrderAndTheJobIsThenFinishedByCounts() {
        runner.run(claim);

        verify(pipeline).runItem(eq(claim), eq(admin), eq(0), any());
        verify(pipeline).runItem(eq(claim), eq(admin), eq(1), any());
        verify(pipeline).runItem(eq(claim), eq(admin), eq(2), any());
        verify(leases, times(3)).renewLease(claim);
        verify(leases).finish(claim, null, null);
    }

    @Test
    void aFailedItemDoesNotStopTheJob() {
        when(pipeline.runItem(eq(claim), eq(admin), eq(1), any())).thenReturn(ItemOutcome.FAILED);

        runner.run(claim);

        verify(pipeline).runItem(eq(claim), eq(admin), eq(2), any());
        verify(leases).finish(claim, null, null);
    }

    @Test
    void aCancelRequestBetweenItemsCancelsTheRestWithoutAnotherCall() {
        when(leases.leaseState(claim)).thenReturn(LeaseState.HELD, LeaseState.CANCEL_REQUESTED);

        runner.run(claim);

        verify(pipeline).runItem(eq(claim), eq(admin), eq(0), any());
        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
        verify(itemState).cancelOpen(claim);
        verify(leases).finish(claim, JobStatus.CANCELLED, null);
    }

    @Test
    void renewsTheLeaseWheneverThePipelineIsAboutToCallTheProvider() {
        when(itemState.pendingIndexes(claim.jobId())).thenReturn(List.of(0));
        when(pipeline.runItem(eq(claim), eq(admin), eq(0), any())).thenAnswer(call -> {
            Runnable beforeProviderCall = call.getArgument(3);
            beforeProviderCall.run();
            beforeProviderCall.run();
            return ItemOutcome.SAVED;
        });

        runner.run(claim);

        // Once before the question, then once per provider call.
        verify(leases, times(3)).renewLease(claim);
    }

    @Test
    void anInterruptedItemEndsTheJobAsCancelledWhateverItReported() {
        when(pipeline.runItem(eq(claim), eq(admin), eq(0), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt(); // what a cancel does to the provider call in progress
            return ItemOutcome.FAILED;
        });

        runner.run(claim);

        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
        verify(itemState).cancelOpen(claim);
        verify(leases).finish(claim, JobStatus.CANCELLED, null);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void aCancelNoticedWhileSavingTheItemInFlightEndsTheJobAsCancelled() {
        when(pipeline.runItem(eq(claim), eq(admin), eq(0), any())).thenReturn(ItemOutcome.CANCELLED);

        runner.run(claim);

        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
        verify(leases).finish(claim, JobStatus.CANCELLED, null);
    }

    @Test
    void aLostLeaseStopsQuietlyWithoutTouchingTheJob() {
        when(leases.leaseState(claim)).thenReturn(LeaseState.HELD, LeaseState.LOST);

        runner.run(claim);

        verify(pipeline).runItem(eq(claim), eq(admin), eq(0), any());
        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
        verify(leases, never()).finish(any(), any(), any());
    }

    @Test
    void aFailedRenewalAlsoCountsAsALostLease() {
        when(leases.renewLease(claim)).thenReturn(false);

        runner.run(claim);

        verifyNoInteractions(pipeline);
        verify(leases, never()).finish(any(), any(), any());
    }

    @Test
    void aLeaseLostInsideTheItemEndsTheRunWithoutFinishing() {
        when(pipeline.runItem(eq(claim), eq(admin), eq(0), any())).thenReturn(ItemOutcome.LEASE_LOST);

        runner.run(claim);

        verify(leases, never()).finish(any(), any(), any());
        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
    }

    @Test
    void aRejectedProviderKeyStopsTheWholeJobAtOnce() {
        when(pipeline.runItem(eq(claim), eq(admin), eq(0), any())).thenReturn(ItemOutcome.FAILED_PROVIDER_AUTH);

        runner.run(claim);

        verify(pipeline, never()).runItem(eq(claim), eq(admin), eq(1), any());
        verify(itemState).failOpen(claim, "GENERATION_PROVIDER_AUTH");
        verify(leases).finish(claim, JobStatus.FAILED, "GENERATION_PROVIDER_AUTH");
    }

    @Test
    void consecutiveProviderOutagesStopTheJobAtTheThreshold() {
        properties.getLimits().setConsecutiveProviderFailuresToStop(2);
        when(itemState.pendingIndexes(5L)).thenReturn(List.of(0, 1, 2, 3));
        when(pipeline.runItem(eq(claim), eq(admin), anyInt(), any())).thenReturn(ItemOutcome.FAILED_PROVIDER_UNAVAILABLE);

        runner.run(claim);

        verify(pipeline, times(2)).runItem(eq(claim), eq(admin), anyInt(), any());
        verify(itemState).failOpen(claim, "GENERATION_PROVIDER_UNAVAILABLE");
        verify(leases).finish(claim, JobStatus.FAILED, "GENERATION_PROVIDER_UNAVAILABLE");
    }

    @Test
    void aSuccessBetweenOutagesResetsTheCounter() {
        properties.getLimits().setConsecutiveProviderFailuresToStop(2);
        when(itemState.pendingIndexes(5L)).thenReturn(List.of(0, 1, 2, 3));
        when(pipeline.runItem(eq(claim), eq(admin), anyInt(), any())).thenReturn(
                ItemOutcome.FAILED_PROVIDER_UNAVAILABLE, ItemOutcome.SAVED,
                ItemOutcome.FAILED_PROVIDER_UNAVAILABLE, ItemOutcome.SAVED);

        runner.run(claim);

        verify(pipeline, times(4)).runItem(eq(claim), eq(admin), anyInt(), any());
        verify(itemState, never()).failOpen(any(), any());
        verify(leases).finish(claim, null, null);
        verify(leases, never()).finish(eq(claim), eq(JobStatus.FAILED), any());
        verify(leases, never()).finish(eq(claim), eq(JobStatus.CANCELLED), isNull());
    }
}
