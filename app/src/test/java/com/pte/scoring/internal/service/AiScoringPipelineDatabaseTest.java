package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringAnswerAiDetail;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.consumer.AiScoringWorker;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerAiDetailRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.stub.StubSpeechScoringClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The dispatch → worker → result path against a real database: the job is
 * sent only after the host's transaction commits, a failed answer can be
 * rescored, and a superseded dispatch can never overwrite a newer one.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false"
})
class AiScoringPipelineDatabaseTest {

    private static final Instant T0 = Instant.parse("2026-10-11T10:00:00Z");
    private static final Duration PAST_STALE_LIMIT = Duration.ofMinutes(15);
    private static final UUID SCORE_TEMPLATE_ID = UUID.randomUUID();

    @Autowired
    private ScoringAnswerRepository answerRepository;
    @Autowired
    private ScoringSessionStateRepository sessionStateRepository;
    @Autowired
    private ScoringAnswerAiDetailRepository detailRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ScoringMethodResolver scoringMethodResolver = mock(ScoringMethodResolver.class);
    private AiScoringResultPersistenceService persistence;
    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        when(scoringMethodResolver.resolve(SCORE_TEMPLATE_ID, ScoringConstants.TASK_TYPE_READ_ALOUD))
                .thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        persistence = new AiScoringResultPersistenceService(answerRepository, sessionStateRepository,
                detailRepository, JsonMapper.builder().build(), mock(ApplicationEventPublisher.class));
        worker = new AiScoringWorker(answerRepository, new StubSpeechScoringClient(), mock(EssayScoringClient.class),
                java.util.List.of(), persistence, Clock.fixed(T0, ZoneOffset.UTC), new AiProviderProperties());
    }

    @Test
    void jobIsSentOnlyAfterTheHostCommitsAndTheWorkerThenScoresTheRow() {
        ScoringAnswer answer = persist(newAnswer());

        inTransaction(() -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            verifyNoInteractions(rabbitTemplate);
            return null;
        });

        AiScoringJob job = sentJobs(1).getFirst();
        ScoringAnswer inFlight = reload(answer);
        assertThat(inFlight.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(inFlight.getAiDispatchedAt()).isEqualTo(T0);
        assertThat(job.dispatchToken()).isEqualTo(inFlight.getDispatchToken());

        inTransaction(() -> {
            worker.onAiScoringJob(job);
            return null;
        });

        ScoringAnswer scored = reload(answer);
        assertThat(scored.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(scored.getRawScore()).isEqualTo(65);
        assertThat(scored.getAiProviderCategory()).isEqualTo(AiProviderCategory.STUB);
        ScoringAnswerAiDetail detail = detail(answer);
        assertThat(detail.getFailureReason()).isNull();
        assertThat(detail.getCriteriaScoresJson()).contains("ORAL_FLUENCY");
    }

    @Test
    void rolledBackHostTransactionSendsNothing() {
        ScoringAnswer answer = persist(newAnswer());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            status.setRollbackOnly();
        });

        verifyNoInteractions(rabbitTemplate);
        ScoringAnswer reloaded = reload(answer);
        assertThat(reloaded.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(reloaded.getDispatchToken()).isZero();
    }

    @Test
    void failedAnswerIsRescoredAndMessagesFromTheOldDispatchChangeNothing() {
        ScoringAnswer answer = persist(newAnswer());
        inTransaction(() -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            return null;
        });
        AiScoringJob firstJob = sentJobs(1).getFirst();
        inTransaction(() -> {
            worker.onDeadLettered(firstJob);
            return null;
        });
        assertThat(reload(answer).getStatus()).isEqualTo(ScoringAnswerStatus.SCORING_FAILED);
        assertThat(detail(answer).getFailureReason()).isEqualTo(AiFailureReason.INTERNAL_ERROR);

        inTransaction(() -> {
            host(T0.plusSeconds(60)).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            return null;
        });
        AiScoringJob rescoreJob = sentJobs(2).getLast();
        assertThat(rescoreJob.dispatchToken()).isGreaterThan(firstJob.dispatchToken());
        inTransaction(() -> {
            worker.onAiScoringJob(rescoreJob);
            return null;
        });

        assertThat(reload(answer).getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(detail(answer).getFailureReason()).isNull();

        inTransaction(() -> {
            worker.onDeadLettered(firstJob);
            worker.onAiScoringJob(firstJob);
            return null;
        });

        ScoringAnswer finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(finalRow.getDispatchToken()).isEqualTo(rescoreJob.dispatchToken());
        assertThat(detail(answer).getFailureReason()).isNull();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void staleRedispatchLosesToAWorkerThatIsStoringTheResult() throws Exception {
        ScoringAnswer answer = newAnswer();
        long token = answer.markAiDispatched(T0);
        persist(answer);
        CountDownLatch workerHoldsTheRow = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> workerRun = executor.submit(() -> inTransaction(() -> {
                boolean persisted = persistence.persistAiScore(answer.getAnswerPublicId(),
                        answer.getAttemptPublicId(), answer.getTenantId(), answer.getSessionPublicId(), token,
                        result());
                answerRepository.flush();
                workerHoldsTheRow.countDown();
                pause();
                return persisted;
            }));
            assertThat(workerHoldsTheRow.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Object> hostRun = executor.submit(() -> inTransaction(() -> {
                host(T0.plus(PAST_STALE_LIMIT)).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
                return null;
            }));

            assertThat(workerRun.get(10, TimeUnit.SECONDS)).isTrue();
            awaitIgnoringFailure(hostRun);
        } finally {
            executor.shutdownNow();
        }

        // The host read the row before the worker committed; its version check makes it fail, not overwrite.
        ScoringAnswer finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(finalRow.getDispatchToken()).isEqualTo(token);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void workerWithASupersededTokenWaitsForTheHostAndThenStoresNothing() throws Exception {
        ScoringAnswer answer = newAnswer();
        long oldToken = answer.markAiDispatched(T0);
        persist(answer);
        CountDownLatch hostHoldsTheRow = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> hostRun = executor.submit(() -> inTransaction(() -> {
                host(T0.plus(PAST_STALE_LIMIT)).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
                answerRepository.flush();
                hostHoldsTheRow.countDown();
                pause();
                return null;
            }));
            assertThat(hostHoldsTheRow.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> workerRun = executor.submit(() -> inTransaction(() -> persistence.persistAiScore(
                    answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getTenantId(),
                    answer.getSessionPublicId(), oldToken, result())));

            hostRun.get(10, TimeUnit.SECONDS);
            assertThat(workerRun.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }

        ScoringAnswer finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(finalRow.getDispatchToken()).isGreaterThan(oldToken);
        assertThat(sentJobs(1).getFirst().dispatchToken()).isEqualTo(finalRow.getDispatchToken());
    }

    @Test
    void scoringWorkIsNewFailedAndLostAnswersButNotJobsInFlightOrScoredRows() {
        Instant staleBefore = T0.minus(PAST_STALE_LIMIT);
        ScoringAnswer pending = newAnswer();
        UUID sessionPublicId = pending.getSessionPublicId();
        UUID tenantId = pending.getTenantId();
        ScoringAnswer failed = inSession(newAnswer(), pending);
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        ScoringAnswer lost = inSession(newAnswer(), pending);
        lost.markAiDispatched(staleBefore.minusSeconds(1));
        ScoringAnswer queuedBeforeDispatchTimes = inSession(newAnswer(), pending);
        queuedBeforeDispatchTimes.setStatus(ScoringAnswerStatus.AI_SCORING);
        ScoringAnswer inFlight = inSession(newAnswer(), pending);
        inFlight.markAiDispatched(staleBefore.plusSeconds(1));
        ScoringAnswer scored = inSession(newAnswer(), pending);
        scored.markScored(70);
        ScoringAnswer otherSession = newAnswer();
        otherSession.setTenantId(tenantId);
        Stream.of(pending, failed, lost, queuedBeforeDispatchTimes, inFlight, scored, otherSession)
                .forEach(this::persist);

        List<ScoringAnswer> work = answerRepository.findScoringWork(sessionPublicId, tenantId, staleBefore);

        assertThat(work).extracting(ScoringAnswer::getAnswerPublicId).containsExactlyInAnyOrder(
                pending.getAnswerPublicId(), failed.getAnswerPublicId(), lost.getAnswerPublicId(),
                queuedBeforeDispatchTimes.getAnswerPublicId());
    }

    private ScoringCommandService host(Instant now) {
        AiScoringDispatcher dispatcher = new AiScoringDispatcher(answerRepository, rabbitTemplate,
                Clock.fixed(now, ZoneOffset.UTC), new AiProviderProperties());
        return new ScoringCommandService(mock(ScoringIngestService.class), answerRepository,
                mock(ObjectiveScoringService.class), dispatcher, scoringMethodResolver);
    }

    private List<AiScoringJob> sentJobs(int expected) {
        ArgumentCaptor<AiScoringJob> jobs = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate, times(expected)).convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY), jobs.capture());
        return jobs.getAllValues();
    }

    private ScoringAnswer newAnswer() {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setScoreTemplatePublicId(SCORE_TEMPLATE_ID);
        answer.setTaskType(ScoringConstants.TASK_TYPE_READ_ALOUD);
        answer.setPayload(UUID.randomUUID().toString());
        answer.setCorrectAnswerText("reference");
        return answer;
    }

    private static ScoringAnswer inSession(ScoringAnswer answer, ScoringAnswer sibling) {
        answer.setSessionPublicId(sibling.getSessionPublicId());
        answer.setTenantId(sibling.getTenantId());
        return answer;
    }

    private ScoringAnswer persist(ScoringAnswer answer) {
        return inTransaction(() -> answerRepository.save(answer));
    }

    private ScoringAnswer reload(ScoringAnswer answer) {
        return answerRepository.findByAnswerPublicId(answer.getAnswerPublicId()).orElseThrow();
    }

    private ScoringAnswerAiDetail detail(ScoringAnswer answer) {
        return detailRepository.findByAnswerPublicId(answer.getAnswerPublicId()).orElseThrow();
    }

    private static AiScoreResult result() {
        return new AiScoreResult(70, Map.of("PRONUNCIATION", 70), "feedback",
                AiProviderCategory.REAL, "AZURE_GEMINI", "model", null);
    }

    private static void awaitIgnoringFailure(Future<?> run) throws Exception {
        try {
            run.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            // A host that lost the race may fail; the assertions on the row are what matter.
        }
    }

    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T inTransaction(Supplier<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> callback.get());
    }
}
