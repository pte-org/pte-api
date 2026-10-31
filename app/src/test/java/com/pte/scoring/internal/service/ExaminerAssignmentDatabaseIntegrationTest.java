package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.examiner.ExaminerAssignmentService;
import com.pte.scoring.internal.service.grading.GradingCohortService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.scoring.dto.request.CommitExaminerAssignmentRequest;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Database-backed service checks for the one-shot examiner commit: concurrency and the 40-attempt budget. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false"
})
class ExaminerAssignmentDatabaseIntegrationTest {

    private static final int ATTEMPT_COUNT = 40;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 20;

    @Autowired
    private GradingAssignmentRepository assignmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void concurrentCommitsOfTheSameTableStoreItExactlyOnce() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        Dependencies dependencies = mockDependencies();
        configurePool(dependencies, tenantId, sessionId,
                List.of(new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId)),
                List.of(examiner(examinerId)));
        CountDownLatch committersAtSessionLock = new CountDownLatch(2);
        AtomicBoolean coordinateCommitters = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (coordinateCommitters.get()) {
                committersAtSessionLock.countDown();
                if (!committersAtSessionLock.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both commit requests did not reach the session lock boundary");
                }
            }
            return null;
        }).when(dependencies.sessionService()).lockForExaminerAssignment(sessionId, tenantId);

        var request = new CommitExaminerAssignmentRequest(
                List.of(new CommitExaminerAssignmentRequest.Entry(attemptId, examinerId)));
        ExaminerAssignmentService service = newService(dependencies);
        coordinateCommitters.set(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        int committed = 0;
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> commitSucceeds(service, sessionId, request, host)),
                    executor.submit(() -> commitSucceeds(service, sessionId, request, host)));
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    committed++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(committed).isEqualTo(1);
        long storedAssignments = inTransaction(() -> assignmentRepository
                .countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId));
        assertThat(storedAssignments).isEqualTo(1);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void fortyAttemptCommitMeetsTheTwoExaminerP95Budget() {
        UUID tenantId = UUID.randomUUID();
        UUID examiner1 = UUID.randomUUID();
        UUID examiner2 = UUID.randomUUID();
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        Dependencies dependencies = mockDependencies();
        ExaminerAssignmentService service = newService(dependencies);
        List<Long> elapsedNanos = new ArrayList<>();

        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            UUID sessionId = UUID.randomUUID();
            List<AttemptSummaryView> attempts = IntStream.range(0, ATTEMPT_COUNT)
                    .mapToObj(index -> new AttemptSummaryView(UUID.randomUUID(), sessionId, UUID.randomUUID(),
                            tenantId))
                    .toList();
            configurePool(dependencies, tenantId, sessionId, attempts,
                    List.of(examiner(examiner1), examiner(examiner2)));
            List<CommitExaminerAssignmentRequest.Entry> entries = IntStream.range(0, ATTEMPT_COUNT)
                    .mapToObj(index -> new CommitExaminerAssignmentRequest.Entry(attempts.get(index).attemptPublicId(),
                            index % 2 == 0 ? examiner1 : examiner2))
                    .toList();

            long startedAt = System.nanoTime();
            var roster = inTransaction(() -> service.commit(sessionId,
                    new CommitExaminerAssignmentRequest(entries), host));
            long elapsed = System.nanoTime() - startedAt;

            assertThat(roster.locked()).isTrue();
            assertThat(roster.rows()).hasSize(ATTEMPT_COUNT);
            var overview = inTransaction(() -> service.overview(sessionId, host));
            assertThat(overview.committedExaminerLoads()).extracting(load -> load.attemptCount()).containsExactly(20L, 20L);
            if (run >= WARMUP_RUNS) {
                elapsedNanos.add(elapsed);
            }
        }

        elapsedNanos.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * elapsedNanos.size()) - 1;
        long p95Nanos = elapsedNanos.get(p95Index);
        System.out.printf("Assignment service + H2 repository commit: 40 attempts, 2 Examiners, "
                + "%d measured runs, p95=%d ms%n", elapsedNanos.size(), TimeUnit.NANOSECONDS.toMillis(p95Nanos));
        assertThat(p95Nanos).isLessThanOrEqualTo(Duration.ofSeconds(2).toNanos());
    }

    @Test
    void aCommittedTableCannotBeCommittedAgain() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        Dependencies dependencies = mockDependencies();
        configurePool(dependencies, tenantId, sessionId,
                List.of(new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId)),
                List.of(examiner(examinerId)));
        var request = new CommitExaminerAssignmentRequest(
                List.of(new CommitExaminerAssignmentRequest.Entry(attemptId, examinerId)));
        ExaminerAssignmentService service = newService(dependencies);

        inTransaction(() -> service.commit(sessionId, request, host));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> inTransaction(() -> service.commit(sessionId, request, host)))
                .isInstanceOf(com.pte.scoring.internal.exception.examiner.InvalidExaminerAssignmentException.class);
        assertThat(inTransaction(() -> assignmentRepository
                .countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))).isEqualTo(1);
        verify(dependencies.cohortService()).ensureManualCohort(sessionId, tenantId, host.userId());
    }

    private boolean commitSucceeds(ExaminerAssignmentService service, UUID sessionId,
            CommitExaminerAssignmentRequest request, CurrentUser host) {
        try {
            inTransaction(() -> service.commit(sessionId, request, host));
            return true;
        } catch (RuntimeException expectedLoser) {
            return false;
        }
    }

    private Dependencies mockDependencies() {
        return new Dependencies(mock(SessionService.class), mock(AttemptService.class),
                mock(ScoringEligibilityQueryService.class), mock(IdentityService.class),
                mock(SessionGradingGateway.class), mock(GradingCohortService.class));
    }

    private void configurePool(Dependencies dependencies, UUID tenantId, UUID sessionId,
            List<AttemptSummaryView> attempts, List<ExaminerIdentityView> examiners) {
        when(dependencies.attemptService().getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(attempts);
        List<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).toList();
        when(dependencies.eligibilityService().findEligibleAttempts(sessionId, tenantId, attemptIds)).thenReturn(
                attempts.stream().map(attempt -> new AiEligibleAttemptView(attempt.attemptPublicId(), 1)).toList());
        List<UUID> examinerIds = examiners.stream().map(ExaminerIdentityView::publicId).toList();
        when(dependencies.identityService().lockActiveExaminers(tenantId, examinerIds)).thenReturn(examiners);
    }

    private ExaminerAssignmentService newService(Dependencies dependencies) {
        return new ExaminerAssignmentService(dependencies.sessionService(), dependencies.attemptService(),
                dependencies.eligibilityService(), dependencies.identityService(), assignmentRepository,
                mock(com.pte.scoring.internal.repository.ExaminerEvaluationRepository.class),
                mock(com.pte.scoring.internal.repository.GradingIssueRepository.class),
                mock(com.pte.scoring.internal.service.examiner.ExaminerWorkRecorder.class),
                mock(ScorePublicationLockService.class), dependencies.gateway(), dependencies.cohortService(), com.pte.TestClocks.fixed());
    }

    private <T> T inTransaction(Supplier<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> callback.get());
    }

    private ExaminerIdentityView examiner(UUID id) {
        return new ExaminerIdentityView(id, "Examiner", "examiner@example.test");
    }

    private record Dependencies(SessionService sessionService, AttemptService attemptService,
            ScoringEligibilityQueryService eligibilityService, IdentityService identityService,
            SessionGradingGateway gateway, GradingCohortService cohortService) {
    }
}
