package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentScopeType;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest;
import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest.AssignmentScopeRequest;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.ExaminerAssignmentBatchRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

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
import static org.mockito.Mockito.when;

/** Database-backed service checks for assignment locking and the 40-attempt allocation path. */
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
    private ExaminerAssignmentBatchRepository batchRepository;

    @Autowired
    private ExaminerAttemptAssignmentRepository assignmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void concurrentConfirmRequestsCommitThePreviewExactlyOnce() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        ExaminerIdentityView examiner = examiner(examinerId);
        Dependencies dependencies = mockDependencies();
        configureSingleAttempt(dependencies, tenantId, sessionId, classId, examinerId, examiner,
                studentId, attemptId);
        CountDownLatch confirmersAtSessionLock = new CountDownLatch(2);
        AtomicBoolean coordinateConfirmers = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (coordinateConfirmers.get()) {
                confirmersAtSessionLock.countDown();
                if (!confirmersAtSessionLock.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both confirm requests did not reach the session lock boundary");
                }
            }
            return null;
        }).when(dependencies.sessionService()).lockForExaminerAssignment(sessionId, tenantId);

        var request = manualRequest(classId, examinerId);
        ExaminerAssignmentService service = newService(dependencies);
        var preview = inTransaction(() -> service.preview(sessionId, request, host));
        assertThat(preview.valid()).isTrue();
        coordinateConfirmers.set(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> inTransaction(
                    () -> service.confirm(sessionId, preview.batchPublicId(), host).status()));
            Future<String> second = executor.submit(() -> inTransaction(
                    () -> service.confirm(sessionId, preview.batchPublicId(), host).status()));

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("COMMITTED");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("COMMITTED");
        } finally {
            executor.shutdownNow();
        }

        long committedAssignments = inTransaction(() -> assignmentRepository
                .countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId));
        assertThat(committedAssignments).isEqualTo(1);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void fortyAttemptPreviewAndCommitMeetTheTwoExaminerP95Budget() {
        UUID tenantId = UUID.randomUUID();
        UUID examiner1 = UUID.randomUUID();
        UUID examiner2 = UUID.randomUUID();
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        ExaminerIdentityView firstExaminer = examiner(examiner1);
        ExaminerIdentityView secondExaminer = examiner(examiner2);
        Dependencies dependencies = mockDependencies();
        ExaminerAssignmentService service = newService(dependencies);
        List<Long> elapsedNanos = new ArrayList<>();

        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            UUID sessionId = UUID.randomUUID();
            UUID classId = UUID.randomUUID();
            List<UUID> students = ids(ATTEMPT_COUNT);
            List<AttemptSummaryView> attempts = IntStream.range(0, ATTEMPT_COUNT)
                    .mapToObj(index -> new AttemptSummaryView(UUID.randomUUID(), sessionId, students.get(index), tenantId))
                    .toList();
            configurePool(dependencies, tenantId, sessionId, classId, examiner1, examiner2,
                    firstExaminer, secondExaminer, students, attempts);
            var request = new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.RANDOM,
                    List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, null)),
                    List.of(examiner1, examiner2));

            long startedAt = System.nanoTime();
            var preview = inTransaction(() -> service.preview(sessionId, request, host));
            var committed = inTransaction(() -> service.confirm(sessionId, preview.batchPublicId(), host));
            long elapsed = System.nanoTime() - startedAt;

            assertThat(preview.valid()).isTrue();
            assertThat(preview.attemptCount()).isEqualTo(ATTEMPT_COUNT);
            assertThat(preview.examinerLoads()).extracting(load -> load.attemptCount()).containsExactly(20L, 20L);
            assertThat(committed.status()).isEqualTo("COMMITTED");
            if (run >= WARMUP_RUNS) {
                elapsedNanos.add(elapsed);
            }
        }

        elapsedNanos.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * elapsedNanos.size()) - 1;
        long p95Nanos = elapsedNanos.get(p95Index);
        System.out.printf("Assignment service + H2 repository preview/commit: 40 attempts, 2 Examiners, "
                + "%d measured runs, p95=%d ms%n", elapsedNanos.size(), TimeUnit.NANOSECONDS.toMillis(p95Nanos));
        assertThat(p95Nanos).isLessThanOrEqualTo(Duration.ofSeconds(2).toNanos());
    }

    private Dependencies mockDependencies() {
        return new Dependencies(mock(SessionService.class), mock(EnrollmentModuleService.class),
                mock(AttemptService.class), mock(ScoringEligibilityQueryService.class), mock(IdentityService.class));
    }

    private void configureSingleAttempt(Dependencies dependencies, UUID tenantId, UUID sessionId, UUID classId,
            UUID examinerId, ExaminerIdentityView examiner, UUID studentId, UUID attemptId) {
        when(dependencies.enrollmentService().findActiveStudentPublicIds(tenantId, classId))
                .thenReturn(List.of(studentId));
        when(dependencies.attemptService().getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(
                List.of(new AttemptSummaryView(attemptId, sessionId, studentId, tenantId)));
        when(dependencies.eligibilityService().findEligibleAttempts(sessionId, tenantId, List.of(attemptId)))
                .thenReturn(List.of(new AiEligibleAttemptView(attemptId, 1)));
        when(dependencies.identityService().findActiveExaminers(tenantId, List.of(examinerId)))
                .thenReturn(List.of(examiner));
        when(dependencies.identityService().lockActiveExaminers(tenantId, List.of(examinerId)))
                .thenReturn(List.of(examiner));
    }

    private void configurePool(Dependencies dependencies, UUID tenantId, UUID sessionId, UUID classId,
            UUID examiner1, UUID examiner2,
            ExaminerIdentityView firstExaminer, ExaminerIdentityView secondExaminer, List<UUID> students,
            List<AttemptSummaryView> attempts) {
        when(dependencies.enrollmentService().findActiveStudentPublicIds(tenantId, classId)).thenReturn(students);
        when(dependencies.attemptService().getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(attempts);
        List<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).toList();
        when(dependencies.eligibilityService().findEligibleAttempts(sessionId, tenantId, attemptIds)).thenReturn(
                attempts.stream().map(attempt -> new AiEligibleAttemptView(attempt.attemptPublicId(), 1)).toList());
        when(dependencies.identityService().findActiveExaminers(tenantId, List.of(examiner1, examiner2)))
                .thenReturn(List.of(firstExaminer, secondExaminer));
        when(dependencies.identityService().lockActiveExaminers(tenantId, List.of(examiner1, examiner2)))
                .thenReturn(List.of(firstExaminer, secondExaminer));
    }

    private ExaminerAssignmentService newService(Dependencies dependencies) {
        return new ExaminerAssignmentService(dependencies.sessionService(), dependencies.enrollmentService(),
                dependencies.attemptService(), dependencies.eligibilityService(), dependencies.identityService(),
                batchRepository, assignmentRepository, JsonMapper.builder().build());
    }

    private <T> T inTransaction(Supplier<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> callback.get());
    }

    private CreateExaminerAssignmentPreviewRequest manualRequest(UUID classId, UUID examinerId) {
        return new CreateExaminerAssignmentPreviewRequest(AssignmentBatchMode.MANUAL,
                List.of(new AssignmentScopeRequest(AssignmentScopeType.CLASS, classId, examinerId)), List.of());
    }

    private ExaminerIdentityView examiner(UUID id) {
        return new ExaminerIdentityView(id, "Examiner", "examiner@example.test");
    }

    private List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(ignored -> UUID.randomUUID()).toList();
    }

    private record Dependencies(SessionService sessionService, EnrollmentModuleService enrollmentService,
            AttemptService attemptService, ScoringEligibilityQueryService eligibilityService,
            IdentityService identityService) {
    }
}
