package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.batch.BatchRequestValidator.ValidatedBatch;
import com.pte.itembank.internal.generation.batch.dto.SubmitGenerationBatchRequest;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Submit through the real service, then a worker loop over the real lease service: claim, write each item the way
 * the pipeline's saved outcome does, finish. The provider side (the stub generator) needs the full application
 * context and is not part of this slice; the batch machinery around it is.
 */
@EnabledIfSystemProperty(named = BatchPostgresDatabase.URL_PROPERTY, matches = ".+")
class BatchSubmitRunPostgresIntegrationTest extends BatchPostgresIntegrationSupport {

    private static final List<BatchRow> ROWS = List.of(
            new BatchRow("MC_READING_SINGLE", CefrLevel.B2, 2, List.of("ocean currents"), ImageKind.AUTO),
            new BatchRow("MC_READING_SINGLE", CefrLevel.C1, 2, List.of(), null));

    private CurrentUser submit() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of(SecurityRoles.PLATFORM_ADMIN));
        when(validator.validate(any(), any())).thenReturn(new ValidatedBatch(QuestionPool.PRACTICE, ROWS, 4));
        batchService.submit(new SubmitGenerationBatchRequest(null, null), caller);
        return caller;
    }

    @Test
    void theRowsRoundTripThroughJsonb() {
        CurrentUser caller = submit();

        QuestionGenerationBatch stored = onlyBatchOf(caller);
        assertThat(stored.getRows()).containsExactlyElementsOf(ROWS);
        String raw = jdbc.queryForObject("select rows ->> 0 from question_generation_batches where id = ?",
                String.class, stored.getId());
        assertThat(raw).contains("\"taskType\"").contains("ocean currents");
    }

    @Test
    void aSecondSubmitWhileTheBatchIsOpenIsRejected() {
        CurrentUser caller = submit();

        assertThatThrownBy(() -> batchService.submit(new SubmitGenerationBatchRequest(null, null), caller))
                .isInstanceOf(GenerationJobConflictException.class);
    }

    @Test
    void aBatchOfTwoRowsRunsItsChildrenOneAfterAnotherToCompleted() {
        CurrentUser caller = submit();
        QuestionGenerationBatch batch = onlyBatchOf(caller);
        assertThat(children(batch.getId())).extracting(QuestionGenerationJob::getStatus)
                .containsExactly(JobStatus.QUEUED, JobStatus.PENDING);

        List<Long> runOrder = new ArrayList<>();
        Optional<JobClaim> claim = leases.claimNext("worker-a");
        while (claim.isPresent()) {
            JobClaim running = claim.get();
            runOrder.add(running.jobId());
            assertThat(leases.claimNext("worker-b")).as("the next child waits for this one").isEmpty();
            saveEveryItem(running);
            leases.finish(running, null, null);
            claim = leases.claimNext("worker-a");
        }

        List<QuestionGenerationJob> children = children(batch.getId());
        assertThat(runOrder).containsExactly(children.get(0).getId(), children.get(1).getId());
        assertThat(children).extracting(QuestionGenerationJob::getStatus)
                .containsExactly(JobStatus.COMPLETED, JobStatus.COMPLETED);
        assertThat(children.get(0).getTopicHint()).isEqualTo("ocean currents");
        assertThat(children.get(1).getCefrLevel()).isEqualTo(CefrLevel.C1);
        QuestionGenerationBatch done = tx(() -> batches.findById(batch.getId()).orElseThrow());
        assertThat(done.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(done.getSucceededCount()).isEqualTo(4);
        assertThat(done.getStartedAt()).isNotNull();
        assertThat(done.getFinishedAt()).isNotNull();
    }

    private void saveEveryItem(JobClaim claim) {
        int count = job(claim.jobId()).getRequestedCount();
        for (int index = 0; index < count; index++) {
            int item = index;
            txRun(() -> items.markSaved(claim.jobId(), item, UUID.randomUUID(), claim.workerId(), Instant.now()));
        }
    }

    private QuestionGenerationBatch onlyBatchOf(CurrentUser caller) {
        return tx(() -> batches.findAll().stream()
                .filter(batch -> batch.getRequestedByUserPublicId().equals(caller.userId()))
                .findFirst().orElseThrow());
    }
}
