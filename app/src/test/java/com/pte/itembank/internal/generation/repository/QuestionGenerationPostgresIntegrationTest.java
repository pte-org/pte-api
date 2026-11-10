package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in: needs the isolated, migrated (V34 applied) Docker database documented in the entity-consolidation runbook.
 * Proves what mocks cannot: the partial unique indexes, the skip-locked claim and the lease-guarded updates.
 */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "logging.level.root=ERROR"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class QuestionGenerationPostgresIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("lifecycle.test.db.url");
        if (!"jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean".equals(url)) {
            throw new IllegalArgumentException("Only the dedicated local lifecycle test database is permitted");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "codex_lifecycle_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired GenerationJobRepository jobs;
    @Autowired GenerationJobItemRepository items;
    @Autowired QuestionRepository questions;
    @Autowired PlatformTransactionManager transactions;

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(status -> action.get());
    }

    private QuestionGenerationJob job(UUID requester, JobStatus status) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(requester);
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setRequestedCount(2);
        job.setStatus(status);
        return tx(() -> jobs.saveAndFlush(job));
    }

    private QuestionGenerationJobItem item(Long jobId, int index) {
        QuestionGenerationJobItem item = new QuestionGenerationJobItem();
        item.setJobId(jobId);
        item.setItemIndex(index);
        item.setUpdatedAt(Instant.now());
        return tx(() -> items.saveAndFlush(item));
    }

    @Test
    void onlyOneActiveJobPerRequesterIsAllowedByTheDatabase() {
        UUID requester = UUID.randomUUID();
        QuestionGenerationJob first = job(requester, JobStatus.QUEUED);

        assertThatThrownBy(() -> job(requester, JobStatus.RUNNING)).isInstanceOf(DataIntegrityViolationException.class);

        tx(() -> jobs.cancelQueued(first.getPublicId(), Instant.now()));
        assertThat(job(requester, JobStatus.QUEUED).getId()).isNotNull();
    }

    @Test
    void aFinishedJobDoesNotBlockTheNextOne() {
        UUID requester = UUID.randomUUID();
        job(requester, JobStatus.COMPLETED);

        assertThat(job(requester, JobStatus.QUEUED).getId()).isNotNull();
    }

    @Test
    void theClaimSkipsAJobWhoseLeaseHasNotExpiredAndTakesAnExpiredOne() {
        QuestionGenerationJob queued = job(UUID.randomUUID(), JobStatus.QUEUED);
        Instant now = Instant.now();
        tx(() -> jobs.markRunning(queued.getId(), "worker-a", now.plus(Duration.ofMinutes(10)), now));

        assertThat(tx(() -> jobs.lockNextClaimable(now))).doesNotContain(queued.getId());
        assertThat(tx(() -> jobs.lockNextClaimable(now.plus(Duration.ofMinutes(11))))).contains(queued.getId());
    }

    @Test
    void aLockedRowIsSkippedByASecondClaimer() {
        QuestionGenerationJob queued = job(UUID.randomUUID(), JobStatus.QUEUED);
        Instant now = Instant.now();

        List<Long> secondClaim = tx(() -> {
            List<Long> first = jobs.lockNextClaimable(now);
            assertThat(first).contains(queued.getId());
            return new TransactionTemplate(transactions,
                    new org.springframework.transaction.support.DefaultTransactionDefinition(
                            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW))
                    .execute(inner -> jobs.lockNextClaimable(now));
        });

        assertThat(secondClaim).doesNotContain(queued.getId());
    }

    @Test
    void leaseGuardedUpdatesChangeNothingForAWorkerThatDoesNotOwnTheJob() {
        QuestionGenerationJob queued = job(UUID.randomUUID(), JobStatus.QUEUED);
        Instant now = Instant.now();
        tx(() -> jobs.markRunning(queued.getId(), "worker-a", now.plus(Duration.ofMinutes(10)), now));
        item(queued.getId(), 0);

        assertThat(tx(() -> jobs.renewLease(queued.getId(), "worker-b", now))).isZero();
        assertThat(tx(() -> items.advance(queued.getId(), 0, ItemStage.GENERATING, "worker-b", now))).isZero();
        assertThat(tx(() -> items.advance(queued.getId(), 0, ItemStage.GENERATING, "worker-a", now))).isEqualTo(1);
        assertThat(tx(() -> jobs.finish(queued.getId(), "worker-b", JobStatus.FAILED, null, now))).isZero();
        assertThat(tx(() -> jobs.finish(queued.getId(), "worker-a", JobStatus.COMPLETED, null, now))).isEqualTo(1);
    }

    @Test
    void countersAreDerivedFromTheItems() {
        QuestionGenerationJob queued = job(UUID.randomUUID(), JobStatus.QUEUED);
        Instant now = Instant.now();
        tx(() -> jobs.markRunning(queued.getId(), "worker-a", now.plus(Duration.ofMinutes(10)), now));
        item(queued.getId(), 0);
        item(queued.getId(), 1);
        tx(() -> items.markSaved(queued.getId(), 0, UUID.randomUUID(), "worker-a", now));
        tx(() -> items.fail(queued.getId(), 1, "GENERATION_INTERNAL_ERROR", null, "worker-a", now));

        tx(() -> jobs.refreshCounters(queued.getId()));

        QuestionGenerationJob refreshed = tx(() -> jobs.findById(queued.getId()).orElseThrow());
        assertThat(refreshed.getSucceededCount()).isEqualTo(1);
        assertThat(refreshed.getFailedCount()).isEqualTo(1);
    }

    @Test
    void aJobSlotCanOnlyEverHoldOneQuestion() {
        UUID jobPublicId = UUID.randomUUID();
        tx(() -> questions.saveAndFlush(generated(jobPublicId, 0, "hash-a")));

        assertThatThrownBy(() -> tx(() -> questions.saveAndFlush(generated(jobPublicId, 0, "hash-b"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tx(() -> questions.saveAndFlush(generated(jobPublicId, 1, "hash-c")).getId())).isNotNull();
    }

    @Test
    void theContentHashIsIndexedButNotUnique() {
        String hash = "same-hash-" + UUID.randomUUID();
        tx(() -> questions.saveAndFlush(generated(UUID.randomUUID(), 0, hash)));
        tx(() -> questions.saveAndFlush(generated(UUID.randomUUID(), 0, hash)));

        assertThat(tx(() -> questions.existsByTaskTypeKeyAndContentHashAndDeletedFalse("READ_ALOUD", hash))).isTrue();
    }

    private Question generated(UUID jobPublicId, int index, String hash) {
        Question question = new Question();
        question.setPromptText("Read this fixture.");
        question.setPteTaskType(PteTaskType.READ_ALOUD);
        question.setTaskTypeKey("READ_ALOUD");
        question.setTaskTypeSection("SPEAKING");
        question.setEverPublished(false);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setGenerationJobPublicId(jobPublicId);
        question.setGenerationItemIndex(index);
        question.setContentHash(hash);
        question.setGenerationMeta("{\"schema\":1}");
        return question;
    }
}
