package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.ai.AiScoringDispatcher;
import com.pte.scoring.internal.service.ai.AiScoringResultPersistenceService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.consumer.AiScoringWorker;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.stub.StubSpeechScoringClient;
import com.pte.scoring.support.EvaluationFixtures;
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
import static org.mockito.ArgumentMatchers.any;
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

    @Autowired
    private AiEvaluationRepository answerRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final SessionGradingGateway gateway = mock(SessionGradingGateway.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AnswerContentReader contentReader = mock(AnswerContentReader.class);
    private AiScoringResultPersistenceService persistence;
    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        when(contentReader.one(any(UUID.class), any(UUID.class))).thenAnswer(call -> Optional.of(
                new SubmittedAnswerView(call.getArgument(0), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), call.getArgument(1), UUID.randomUUID(),
                        ScoringConstants.TASK_TYPE_READ_ALOUD, "audio-ref", "reference", null, false, null, null,
                        null, null, null, "SPEAKING", null, null, null, null, null)));
        persistence = new AiScoringResultPersistenceService(answerRepository, gateway,
                JsonMapper.builder().build(), mock(ApplicationEventPublisher.class), null, java.time.Clock.systemUTC());
        worker = new AiScoringWorker(answerRepository, new StubSpeechScoringClient(), mock(EssayScoringClient.class),
                java.util.List.of(), persistence, Clock.fixed(T0, ZoneOffset.UTC), new AiProviderProperties(), contentReader);
    }

    @Test
    void jobIsSentOnlyAfterTheHostCommitsAndTheWorkerThenScoresTheRow() {
        AiEvaluation answer = persist(newAnswer());

        inTransaction(() -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            verifyNoInteractions(rabbitTemplate);
            return null;
        });

        AiScoringJob job = sentJobs(1).getFirst();
        AiEvaluation inFlight = reload(answer);
        assertThat(inFlight.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(inFlight.getAiDispatchedAt()).isEqualTo(T0);
        assertThat(job.dispatchToken()).isEqualTo(inFlight.getDispatchToken());

        inTransaction(() -> {
            worker.onAiScoringJob(job);
            return null;
        });

        AiEvaluation scored = reload(answer);
        assertThat(scored.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(scored.getRawScore()).isEqualTo(65);
        assertThat(scored.getAiProviderCategory()).isEqualTo(AiProviderCategory.STUB);
        assertThat(scored.getFailureReason()).isNull();
        assertThat(scored.getCriteriaScoresJson()).contains("ORAL_FLUENCY");
    }

    @Test
    void rolledBackHostTransactionSendsNothing() {
        AiEvaluation answer = persist(newAnswer());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            status.setRollbackOnly();
        });

        verifyNoInteractions(rabbitTemplate);
        AiEvaluation reloaded = reload(answer);
        assertThat(reloaded.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(reloaded.getDispatchToken()).isZero();
    }

    @Test
    void failedAnswerIsRescoredAndMessagesFromTheOldDispatchChangeNothing() {
        AiEvaluation answer = persist(newAnswer());
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
        assertThat(reload(answer).getFailureReason()).isEqualTo(AiFailureReason.INTERNAL_ERROR);

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
        assertThat(reload(answer).getFailureReason()).isNull();

        inTransaction(() -> {
            worker.onDeadLettered(firstJob);
            worker.onAiScoringJob(firstJob);
            return null;
        });

        AiEvaluation finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(finalRow.getDispatchToken()).isEqualTo(rescoreJob.dispatchToken());
        assertThat(reload(answer).getFailureReason()).isNull();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void staleRedispatchLosesToAWorkerThatIsStoringTheResult() throws Exception {
        AiEvaluation answer = newAnswer();
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
        AiEvaluation finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(finalRow.getDispatchToken()).isEqualTo(token);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void workerWithASupersededTokenWaitsForTheHostAndThenStoresNothing() throws Exception {
        AiEvaluation answer = newAnswer();
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

        AiEvaluation finalRow = reload(answer);
        assertThat(finalRow.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(finalRow.getDispatchToken()).isGreaterThan(oldToken);
        assertThat(sentJobs(1).getFirst().dispatchToken()).isEqualTo(finalRow.getDispatchToken());
    }

    @Test
    void scoringWorkIsNewFailedAndLostAnswersButNotJobsInFlightOrScoredRows() {
        Instant staleBefore = T0.minus(PAST_STALE_LIMIT);
        AiEvaluation pending = newAnswer();
        UUID sessionPublicId = pending.getSessionPublicId();
        UUID tenantId = pending.getTenantId();
        AiEvaluation failed = newAnswer(tenantId, sessionPublicId);
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        AiEvaluation lost = newAnswer(tenantId, sessionPublicId);
        lost.markAiDispatched(staleBefore.minusSeconds(1));
        AiEvaluation queuedBeforeDispatchTimes = newAnswer(tenantId, sessionPublicId);
        queuedBeforeDispatchTimes.setStatus(ScoringAnswerStatus.AI_SCORING);
        AiEvaluation inFlight = newAnswer(tenantId, sessionPublicId);
        inFlight.markAiDispatched(staleBefore.plusSeconds(1));
        AiEvaluation scored = newAnswer(tenantId, sessionPublicId);
        scored.markScored(70, java.time.Instant.now());
        AiEvaluation otherSession = newAnswer(tenantId, UUID.randomUUID());
        Stream.of(pending, failed, lost, queuedBeforeDispatchTimes, inFlight, scored, otherSession)
                .forEach(this::persist);

        List<AiEvaluation> work = answerRepository.findScoringWork(sessionPublicId, tenantId, staleBefore);

        assertThat(work).extracting(AiEvaluation::getAnswerPublicId).containsExactlyInAnyOrder(
                pending.getAnswerPublicId(), failed.getAnswerPublicId(), lost.getAnswerPublicId(),
                queuedBeforeDispatchTimes.getAnswerPublicId());
    }

    private ScoringCommandService host(Instant now) {
        AiScoringDispatcher dispatcher = new AiScoringDispatcher(answerRepository, rabbitTemplate,
                Clock.fixed(now, ZoneOffset.UTC), new AiProviderProperties());
        return new ScoringCommandService(mock(ScoringIngestService.class), answerRepository,
                mock(ObjectiveScoringService.class), dispatcher, contentReader,
                mock(com.pte.session.SessionService.class), mock(ScorePublicationLockService.class),
                mock(ApplicationEventPublisher.class), java.time.Clock.systemUTC());
    }

    private List<AiScoringJob> sentJobs(int expected) {
        ArgumentCaptor<AiScoringJob> jobs = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate, times(expected)).convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY), jobs.capture());
        return jobs.getAllValues();
    }

    private AiEvaluation newAnswer() {
        return newAnswer(UUID.randomUUID(), UUID.randomUUID());
    }

    private AiEvaluation newAnswer(UUID tenantId, UUID sessionPublicId) {
        return EvaluationFixtures.ai(tenantId, sessionPublicId, ScoringConstants.TASK_TYPE_READ_ALOUD,
                ScoringMethod.AI_SPEECH);
    }

    private AiEvaluation persist(AiEvaluation answer) {
        return inTransaction(() -> answerRepository.save(answer));
    }

    private AiEvaluation reload(AiEvaluation answer) {
        return answerRepository.findByAnswerPublicId(answer.getAnswerPublicId()).orElseThrow();
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
