package com.pte.billing.internal.service;

import com.pte.billing.domain.*;
import com.pte.billing.domain.enums.*;
import com.pte.billing.internal.exception.PlanLifecycleException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.*;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.*;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.*;
import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in: use only the isolated, migrated Docker database documented in the plan. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "logging.level.root=ERROR"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PlanService.class, QuestionDeletionService.class, AuditLogService.class, LicenseCodePersistenceService.class})
@EnabledIfSystemProperty(named = "lifecycle.test.db.url", matches = ".+")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ArchiveLifecyclePostgresIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("lifecycle.test.db.url");
        if (!"jdbc:postgresql://127.0.0.1:55439/lifecycle_test?currentSchema=migration_clean".equals(url)) {
            throw new IllegalArgumentException("Only the dedicated local lifecycle test database is permitted");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "codex_lifecycle_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired PlanRepository plans;
    @Autowired QuestionRepository questions;
    @Autowired LicenseCodeRepository codes;
    @Autowired OrderRepository orders;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired com.pte.assessment.internal.repository.ExamSnapshotRepository snapshots;
    @Autowired PlanService planService;
    @Autowired QuestionDeletionService questionDeletion;
    @Autowired LicenseCodePersistenceService issuance;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    private void runTx(Runnable action) { tx(() -> { action.run(); return null; }); }
    private Plan plan(PlanStatus status) {
        return tx(() -> {
            Plan plan = new Plan(); plan.setName("Lifecycle fixture"); plan.setType(PlanType.EXAM_PACKAGE);
            plan.setCurrency("VND"); plan.setPrice(BigDecimal.TEN); plan.setDurationDays(30);
            plan.setMaxStudentsPerSession(20); plan.setStatus(status);
            return plans.saveAndFlush(plan);
        });
    }
    private Question draft() {
        return tx(() -> {
            Question question = new Question(); question.setTitle("Lifecycle draft"); question.setPromptText("Read this fixture.");
            question.setPteTaskType(PteTaskType.READ_ALOUD); question.setTaskTypeKey("READ_ALOUD");
            question.setTaskTypeSection("SPEAKING"); question.setVisibility(Visibility.SHARED);
            question.setEverPublished(false); question.setRevisionGroupPublicId(UUID.randomUUID());
            return questions.saveAndFlush(question);
        });
    }
    private long auditCount(UUID id) {
        return jdbc.queryForObject("select count(*) from audit_logs where aggregate_id = ?", Long.class, id.toString());
    }

    @Test void planDeletionCommitsTombstoneAndOneAuditAndNormalReadsReturnNotFound() {
        Plan plan = plan(PlanStatus.DRAFT);
        planService.deleteDraft(plan.getPublicId(), admin);
        planService.deleteDraft(plan.getPublicId(), admin);
        assertThat(tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow().isDeleted())).isTrue();
        assertThat(auditCount(plan.getPublicId())).isEqualTo(1);
        assertThatThrownBy(() -> planService.get(plan.getPublicId())).isInstanceOf(PlanNotFoundException.class);
        assertThat(planService.listForAdmin()).noneMatch(p -> p.publicId().equals(plan.getPublicId()));
    }

    @Test void questionDeletionPreservesRowButHidesDetailsBatchAndStats() {
        Question question = draft();
        questionDeletion.deleteDraft(question.getPublicId(), admin);
        questionDeletion.deleteDraft(question.getPublicId(), admin);
        assertThat(tx(() -> questions.findById(question.getId()).orElseThrow().isDeleted())).isTrue();
        assertThat(auditCount(question.getPublicId())).isEqualTo(1);
        assertThat(tx(() -> questions.findWithOptionsByPublicId(question.getPublicId()))).isEmpty();
        assertThat(tx(() -> questions.findWithOptionsByPublicIdIn(List.of(question.getPublicId())))).isEmpty();
        assertThat(tx(() -> questions.publishedSharedIdsByTaskTypeKey("READ_ALOUD"))).doesNotContain(question.getPublicId());
    }

    @Test void oldCancelledOrderAndExpiredCodeStillProtectDraft() {
        Plan plan = plan(PlanStatus.DRAFT);
        runTx(() -> {
            Order order = Order.pending(UUID.randomUUID(), plan.getPublicId(), System.nanoTime(), BigDecimal.TEN, "VND");
            order.cancel(); orders.saveAndFlush(order);
            LicenseCode code = LicenseCode.issue("TEST-" + UUID.randomUUID().toString().substring(0, 20), plan.getPublicId(), admin.userId(), Instant.now(), Instant.now().minusSeconds(60));
            code.expire(); codes.saveAndFlush(code);
        });
        assertThat(planService.get(plan.getPublicId()).canDeleteDraft()).isFalse();
        assertThatThrownBy(() -> planService.deleteDraft(plan.getPublicId(), admin)).isInstanceOf(PlanLifecycleException.class);
        assertThat(tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow().isDeleted())).isFalse();
    }

    @Test void auditFailureRollsBackDeletionInTheSameTransaction() {
        Plan plan = plan(PlanStatus.DRAFT);
        AuditLogService audit = mock(AuditLogService.class);
        doThrow(new IllegalStateException("Injected audit failure")).when(audit).record(any(), anyString(), anyString(), anyString(), anyString());
        PlanService failingService = new PlanService(plans, audit);
        assertThatThrownBy(() -> runTx(() -> failingService.deleteDraft(plan.getPublicId(), admin))).isInstanceOf(IllegalStateException.class);
        assertThat(tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow().isDeleted())).isFalse();
        assertThat(auditCount(plan.getPublicId())).isZero();
    }

    @Test void tenIssueArchiveRacesNeverLeaveRedeemableCodeOnArchivedPlan() throws Exception {
        for (int i = 0; i < 10; i++) {
            Plan plan = plan(PlanStatus.ACTIVE);
            LicenseCode code = LicenseCode.issue("TEST-" + UUID.randomUUID().toString().substring(0, 20), plan.getPublicId(), admin.userId(), Instant.now(), null);
            List<Throwable> results = race(() -> issuance.save(code), () -> planService.archive(plan.getPublicId()));
            assertThat(results.stream().filter(t -> t != null).count()).isEqualTo(1);
            boolean archived = tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow().getStatus() == PlanStatus.ARCHIVED);
            boolean issued = tx(() -> codes.findByCode(code.getCode()).isPresent());
            assertThat(archived && issued).isFalse();
            assertThat(archived || issued).isTrue();
        }
    }

    @Test void tenDeleteActivateRacesHaveExactlyOneDurableWinner() throws Exception {
        for (int i = 0; i < 10; i++) {
            Plan plan = plan(PlanStatus.DRAFT);
            List<Throwable> results = race(() -> planService.deleteDraft(plan.getPublicId(), admin), () -> planService.activate(plan.getPublicId()));
            assertThat(results.stream().filter(t -> t != null).count()).isEqualTo(1);
            Plan result = tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow());
            assertThat(result.isDeleted() && result.getStatus() == PlanStatus.ACTIVE).isFalse();
            assertThat(result.isDeleted() || result.getStatus() == PlanStatus.ACTIVE).isTrue();
            assertThat(auditCount(plan.getPublicId())).isEqualTo(result.isDeleted() ? 1 : 0);
        }
    }

    @Test void tenIssueEntitlementEditRacesSerializeAndFurtherEditsAreBlocked() throws Exception {
        for (int i = 0; i < 10; i++) {
            Plan plan = plan(PlanStatus.ACTIVE);
            LicenseCode code = LicenseCode.issue("TEST-" + UUID.randomUUID().toString().substring(0, 20),
                    plan.getPublicId(), admin.userId(), Instant.now(), null);
            PlanRequest edit = new PlanRequest(plan.getName(), null, "EXAM_PACKAGE", BigDecimal.TEN,
                    "VND", 60, 40, null);
            List<Throwable> results = race(() -> issuance.save(code), () -> planService.update(plan.getPublicId(), edit));
            assertThat(results.getFirst()).isNull();
            if (results.get(1) != null) assertThat(results.get(1)).isInstanceOf(PlanLifecycleException.class);
            Plan persisted = tx(() -> plans.findByPublicId(plan.getPublicId()).orElseThrow());
            assertThat(persisted.getDurationDays()).isEqualTo(results.get(1) == null ? 60 : 30);
            assertThat(persisted.getMaxStudentsPerSession()).isEqualTo(results.get(1) == null ? 40 : 20);
            assertThat(tx(() -> codes.findByCode(code.getCode()))).isPresent();
            PlanRequest furtherEdit = new PlanRequest(plan.getName(), null, "EXAM_PACKAGE", BigDecimal.TEN,
                    "VND", 90, 50, null);
            assertThatThrownBy(() -> planService.update(plan.getPublicId(), furtherEdit))
                    .isInstanceOf(PlanLifecycleException.class);
        }
    }

    @Test void retirementPreservesPaidReceiptAndSubscriptionContract() {
        Plan plan = plan(PlanStatus.ACTIVE);
        UUID tenant = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiry = start.plusSeconds(30 * 86400L);
        Subscription subscription = tx(() -> {
            Subscription row = new Subscription();
            row.setTenantId(tenant); row.setPlanId(plan.getPublicId());
            row.setLicenseKey(UUID.randomUUID().toString().replace("-", ""));
            row.setStartsAt(start); row.setExpiresAt(expiry); row.setMaxStudentsPerSession(20);
            row.setActivationSource(ActivationSource.PAYMENT);
            return subscriptions.saveAndFlush(row);
        });
        Order receipt = tx(() -> {
            Order row = Order.pending(tenant, plan.getPublicId(), System.nanoTime(), BigDecimal.TEN, "VND");
            row.markPaid(start); return orders.saveAndFlush(row);
        });
        planService.archive(plan.getPublicId());
        Subscription retained = tx(() -> subscriptions.findByPublicId(subscription.getPublicId()).orElseThrow());
        assertThat(retained.getExpiresAt()).isEqualTo(expiry);
        assertThat(retained.getMaxStudentsPerSession()).isEqualTo(20);
        assertThat(retained.isUsableAt(Instant.now())).isTrue();
        Order retainedReceipt = tx(() -> orders.findById(receipt.getId()).orElseThrow());
        assertThat(retainedReceipt.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(retainedReceipt.getCurrency()).isEqualTo("VND");
        assertThat(retainedReceipt.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(planService.listActive()).noneMatch(p -> p.publicId().equals(plan.getPublicId()));
    }

    @Test void staleQuestionWriterCannotUndoCommittedTombstone() {
        Question question = draft();
        Question stale = tx(() -> questions.findById(question.getId()).orElseThrow());
        questionDeletion.deleteDraft(question.getPublicId(), admin);
        stale.setStatus(QuestionStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> runTx(() -> questions.saveAndFlush(stale)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(tx(() -> questions.findById(question.getId()).orElseThrow().isDeleted())).isTrue();
        assertThat(auditCount(question.getPublicId())).isEqualTo(1);
    }

    @Test void publishedSnapshotRetainsPinnedContentAfterSourceQuestionArchive() {
        Question question = draft();
        runTx(() -> {
            Question published = questions.findById(question.getId()).orElseThrow();
            published.setStatus(QuestionStatus.APPROVED); published.setEverPublished(true);
        });
        com.pte.assessment.domain.ExamSnapshot snapshot = tx(() -> {
            var frozen = new com.pte.assessment.domain.ExamSnapshot();
            frozen.setName("Pinned lifecycle fixture"); frozen.setVersion(1);
            frozen.setSourceBlueprintPublicId(UUID.randomUUID());
            frozen.setScoreTemplatePublicId(UUID.randomUUID()); frozen.setScoreTemplateVersion(1);
            var item = new com.pte.assessment.domain.SnapshotItem();
            item.setSourceQuestionPublicId(question.getPublicId()); item.setPteTaskType(PteTaskType.READ_ALOUD);
            item.setTaskTypeKey("READ_ALOUD"); item.setSection(PteSection.SPEAKING); item.setOrderIndex(1);
            item.setTitle(question.getTitle()); item.setPromptText(question.getPromptText()); item.setOptionsJson("[]");
            frozen.addItem(item); return snapshots.saveAndFlush(frozen);
        });
        ItembankService itembank = new ItembankService(questions, mock(QuestionValidationHelper.class), new ItembankAccessPolicy());
        runTx(() -> itembank.archive(question.getPublicId(), admin));
        assertThatThrownBy(() -> questionDeletion.deleteDraft(question.getPublicId(), admin))
                .isInstanceOf(com.pte.itembank.internal.exception.QuestionDeletionException.class);
        runTx(() -> {
            var retained = snapshots.findById(snapshot.getId()).orElseThrow();
            assertThat(retained.getScoreTemplatePublicId()).isEqualTo(snapshot.getScoreTemplatePublicId());
            assertThat(retained.getItems()).hasSize(1);
            assertThat(retained.getItems().getFirst().getPromptText()).isEqualTo(question.getPromptText());
            assertThat(retained.getItems().getFirst().getOptionsJson()).isEqualTo("[]");
            assertThat(retained.getItems().getFirst().getSourceQuestionPublicId()).isEqualTo(question.getPublicId());
        });
        assertThat(tx(() -> questions.publishedSharedIdsByTaskTypeKey("READ_ALOUD"))).doesNotContain(question.getPublicId());
    }

    @Test void tenDeleteSubmitRacesCannotResurrectDeletedQuestion() throws Exception {
        ItembankService itembank = new ItembankService(questions, mock(QuestionValidationHelper.class), new ItembankAccessPolicy());
        for (int i = 0; i < 10; i++) {
            Question question = draft();
            List<Throwable> results = race(() -> questionDeletion.deleteDraft(question.getPublicId(), admin),
                    () -> runTx(() -> itembank.submitApproval(question.getPublicId(), admin)));
            assertThat(results.stream().filter(t -> t != null).count()).isEqualTo(1);
            Question result = tx(() -> questions.findById(question.getId()).orElseThrow());
            assertThat(result.isDeleted() && result.getStatus() == QuestionStatus.PENDING_APPROVAL).isFalse();
            assertThat(result.isDeleted() || result.getStatus() == QuestionStatus.PENDING_APPROVAL).isTrue();
            assertThat(auditCount(question.getPublicId())).isEqualTo(result.isDeleted() ? 1 : 0);
        }
    }

    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> one = executor.submit(contender(first, ready, start));
            Future<Throwable> two = executor.submit(contender(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return java.util.Arrays.asList(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        }
    }
    private Callable<Throwable> contender(Runnable action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race start timed out");
            try { action.run(); return null; } catch (RuntimeException failure) { return failure; }
        };
    }
}
