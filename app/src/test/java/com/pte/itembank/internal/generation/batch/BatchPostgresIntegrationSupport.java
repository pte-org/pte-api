package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.worker.JobLeaseService;
import com.pte.shared.audit.AuditLogService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Real batch services and repositories over the dedicated, migrated {@code pte_lifecycle_test} database, every
 * call in its own committed transaction. The generation tables are emptied before each test (the claim and the
 * reconciler look at every job and batch), which is why only that throwaway database is accepted.
 * Each concrete test class carries the opt-in {@code @EnabledIfSystemProperty} itself (JUnit does not inherit it).
 */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "logging.level.root=ERROR"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JobLeaseService.class, BatchChainingService.class, QuestionGenerationBatchService.class,
        BatchPostgresIntegrationSupport.Beans.class})
@Timeout(value = 90, unit = TimeUnit.SECONDS)
abstract class BatchPostgresIntegrationSupport {

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        QuestionGenerationProperties questionGenerationProperties() {
            return new QuestionGenerationProperties();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        BatchPostgresDatabase.migrate("public", "latest");
        properties.add("spring.datasource.url", BatchPostgresDatabase::url);
        properties.add("spring.datasource.username", BatchPostgresDatabase::user);
        properties.add("spring.datasource.password", BatchPostgresDatabase::password);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockitoBean GenerationLimits limits;
    @MockitoBean BatchRequestValidator validator;
    @MockitoBean AuditLogService audit;
    @MockitoBean QuestionGenerationBatchQueryService batchQueries;

    @Autowired GenerationJobRepository jobs;
    @Autowired GenerationJobItemRepository items;
    @Autowired GenerationBatchRepository batches;
    @Autowired JobLeaseService leases;
    @Autowired BatchChainingService chaining;
    @Autowired QuestionGenerationBatchService batchService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void emptyGenerationTablesAndSetLimits() {
        jdbc.execute("delete from question_generation_job_items");
        jdbc.execute("delete from question_generation_jobs");
        jdbc.execute("delete from question_generation_batches");
        when(limits.maxConcurrentJobs()).thenReturn(3);
        when(limits.maxItemsPerJob()).thenReturn(10);
        when(limits.maxItemsPerUserPerDay()).thenReturn(1_000);
        when(limits.maxItemsPerDayGlobal()).thenReturn(10_000);
        when(batchQueries.require(any(), any())).thenAnswer(call ->
                batches.findByPublicIdAndDeletedFalse(call.getArgument(0)).orElseThrow());
    }

    <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> action.get());
    }

    void txRun(Runnable action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> action.run());
    }

    /** A batch of {@code children} children with {@code perChild} items each: seq 0 QUEUED, the rest PENDING. */
    QuestionGenerationBatch batchWithChildren(UUID author, int children, int perChild) {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setRequestedByUserPublicId(author);
        batch.setPool(QuestionPool.PRACTICE);
        batch.setRows(List.of(new BatchRow("MC_READING_SINGLE", CefrLevel.B2, children * perChild, List.of(), null)));
        batch.setTotalRequested(children * perChild);
        QuestionGenerationBatch saved = tx(() -> batches.saveAndFlush(batch));
        for (int seq = 0; seq < children; seq++) {
            QuestionGenerationJob child = newJob(author, true, seq == 0 ? JobStatus.QUEUED : JobStatus.PENDING, perChild);
            child.setBatchId(saved.getId());
            child.setBatchSeq(seq);
            child.setBatchRowIndex(0);
            QuestionGenerationJob savedChild = tx(() -> jobs.saveAndFlush(child));
            addItems(savedChild.getId(), perChild);
        }
        return saved;
    }

    QuestionGenerationJob standalone(UUID author, boolean saves) {
        QuestionGenerationJob job = tx(() -> jobs.saveAndFlush(newJob(author, saves, JobStatus.QUEUED, 1)));
        addItems(job.getId(), 1);
        return job;
    }

    List<QuestionGenerationJob> children(Long batchId) {
        return tx(() -> jobs.findByBatchIdOrderByBatchSeqAsc(batchId));
    }

    QuestionGenerationJob job(Long id) {
        return tx(() -> jobs.findById(id).orElseThrow());
    }

    BatchStatus batchStatus(Long batchId) {
        return tx(() -> batches.findById(batchId).orElseThrow().getStatus());
    }

    long sumForAuthor(UUID author) {
        return tx(() -> jobs.sumRequestedSinceByUser(author, Instant.now().minusSeconds(3_600)));
    }

    /** Backends of this database currently waiting on a row lock: proof the other transaction is blocked. */
    int lockWaiters() {
        Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = "
                + "current_database() and wait_event_type = 'Lock'", Integer.class);
        return waiting == null ? 0 : waiting;
    }

    private void addItems(Long jobId, int count) {
        List<QuestionGenerationJobItem> created = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            QuestionGenerationJobItem item = new QuestionGenerationJobItem();
            item.setJobId(jobId);
            item.setItemIndex(index);
            item.setStage(ItemStage.QUEUED);
            item.setUpdatedAt(Instant.now());
            created.add(item);
        }
        txRun(() -> items.saveAllAndFlush(created));
    }

    private static QuestionGenerationJob newJob(UUID author, boolean saves, JobStatus status, int count) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(author);
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setRequestedCount(count);
        job.setSaveQuestions(saves);
        job.setStatus(status);
        return job;
    }
}
