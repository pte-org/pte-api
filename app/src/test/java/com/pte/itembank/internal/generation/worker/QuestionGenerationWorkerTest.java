package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationWorkerTest {

    private final JobLeaseService leases = mock(JobLeaseService.class);
    private final GenerationJobRunner runner = mock(GenerationJobRunner.class);
    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    private final QuestionGenerationWorker worker = new QuestionGenerationWorker(leases, runner, executor,
            org.mockito.Mockito.mock(com.pte.itembank.internal.generation.live.GenerationLiveFeed.class));
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "MC_READING_SINGLE",
            CefrLevel.B2, QuestionPool.PRACTICE, null, "worker");

    @Test
    void anEmptyPollRunsNothing() {
        when(leases.claimNext(anyString())).thenReturn(Optional.empty());

        worker.tick();
        worker.tick();

        verify(leases, times(2)).claimNext(anyString());
        verify(executor, times(0)).execute(any());
    }

    @Test
    void aClaimedJobRunsOnTheDedicatedExecutorNotOnTheSchedulerThread() {
        when(leases.claimNext(anyString())).thenReturn(Optional.of(claim));

        worker.tick();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        verify(runner, times(0)).run(any());
        task.getValue().run();
        verify(runner).run(claim);
    }

    @Test
    void whileAJobIsRunningNoSecondJobIsClaimed() {
        when(leases.claimNext(anyString())).thenReturn(Optional.of(claim));

        worker.tick();
        worker.tick();

        verify(leases, times(1)).claimNext(anyString());
    }

    @Test
    void theWorkerFreesItselfWhenTheJobEndsEvenIfItThrows() {
        when(leases.claimNext(anyString())).thenReturn(Optional.of(claim));
        doThrow(new IllegalStateException("boom")).when(runner).run(claim);
        worker.tick();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());

        task.getValue().run();
        worker.tick();

        verify(leases, times(2)).claimNext(anyString());
    }

    @Test
    void aFailingClaimIsSwallowedAndRetriedOnTheNextPoll() {
        when(leases.claimNext(anyString())).thenThrow(new IllegalStateException("db down"))
                .thenReturn(Optional.empty());

        worker.tick();
        worker.tick();

        verify(leases, times(2)).claimNext(anyString());
    }

    @Test
    void aRejectedSubmissionReleasesTheWorker() {
        when(leases.claimNext(anyString())).thenReturn(Optional.of(claim));
        doThrow(new RejectedExecutionException()).when(executor).execute(any());

        worker.tick();
        worker.tick();

        verify(leases, times(2)).claimNext(anyString());
        assertThat(claim).isNotNull();
    }
}
