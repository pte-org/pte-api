package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.consumer.AiScoringWorker;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.service.ai.AiScoringDispatcher;
import com.pte.scoring.internal.service.ai.AiScoringResultPersistenceService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.scorer.WriteEssayScorer;
import com.pte.scoring.internal.vendor.stub.StubSpeechScoringClient;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Against a real database: an essay whose model answers keep breaking the schema ends SCORING_FAILED with
 * PROVIDER_RESPONSE_INVALID, the host's existing scoring request ({@code findScoringWork}) re-queues it under a new
 * token, and the rescored row stores the Pearson-scaled traits and its rubric version.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false"
})
class SchemaViolationRescoreDatabaseTest {

    private static final Instant T0 = Instant.parse("2026-11-05T10:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final String INVALID = "{\"CONTENT\": 7, \"DEVELOPMENT_STRUCTURE_COHERENCE\": 4, \"GRAMMAR\": 2, "
            + "\"GENERAL_LINGUISTIC_RANGE\": 4, \"VOCABULARY_RANGE\": 1, \"SPELLING\": 2, \"feedback\": \"\", "
            + "\"errors\": []}";
    private static final String VALID = INVALID.replace("\"CONTENT\": 7", "\"CONTENT\": 4");

    @Autowired
    private AiEvaluationRepository answerRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final GeminiRestClient gemini = mock(GeminiRestClient.class);
    private final AnswerContentReader contentReader = mock(AnswerContentReader.class);
    private final RubricResolver rubricResolver = mock(RubricResolver.class);
    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        when(contentReader.one(any(UUID.class), any(UUID.class))).thenAnswer(call -> Optional.of(
                new SubmittedAnswerView(call.getArgument(0), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), call.getArgument(1), UUID.randomUUID(),
                        ScoringConstants.TASK_TYPE_WRITE_ESSAY, essay(), null, null, false,
                        "Should cities ban cars?", null, null, null, null, "WRITING", null, null, null, null, null)));
        String we = ScoringConstants.TASK_TYPE_WRITE_ESSAY;
        when(rubricResolver.resolveForExamDefinition(any(), eq(we))).thenReturn(new ResolvedRubric(VERSION,
                new EffectiveRubric(CATALOG.find(we).orElseThrow(), CATALOG.defaultParameters(we))));
        AiScoringResultPersistenceService persistence = new AiScoringResultPersistenceService(answerRepository,
                mock(SessionGradingGateway.class), JSON, mock(ApplicationEventPublisher.class), null,
                com.pte.TestClocks.fixed());
        worker = new AiScoringWorker(answerRepository, new StubSpeechScoringClient(), mock(EssayScoringClient.class),
                List.of(new WriteEssayScorer(gemini, JSON)), persistence, Clock.fixed(T0, ZoneOffset.UTC),
                new AiProviderProperties(), contentReader, rubricResolver);
    }

    @Test
    void anEssayFailedOnSchemaViolationsIsRequeuedByTheHostAndRescoredOnThePearsonScale() {
        AiEvaluation answer = inTransaction(() -> answerRepository.save(EvaluationFixtures.ai(UUID.randomUUID(),
                UUID.randomUUID(), ScoringConstants.TASK_TYPE_WRITE_ESSAY, ScoringMethod.AI_TEXT)));
        when(gemini.generate(any())).thenReturn(response(INVALID), response(INVALID), response(INVALID),
                response(VALID));

        inTransaction(() -> {
            host(T0).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            return null;
        });
        AiScoringJob first = sentJobs(1).getFirst();
        inTransaction(() -> {
            worker.onAiScoringJob(first);
            return null;
        });

        AiEvaluation failed = reload(answer);
        assertThat(failed.getStatus()).isEqualTo(ScoringAnswerStatus.SCORING_FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
        assertThat(failed.getRawScore()).isNull();
        verify(gemini, times(3)).generate(any());

        inTransaction(() -> {
            host(T0.plusSeconds(60)).requestScoring(answer.getSessionPublicId(), answer.getTenantId());
            return null;
        });
        AiScoringJob rescore = sentJobs(2).getLast();
        assertThat(rescore.dispatchToken()).isGreaterThan(first.dispatchToken());
        inTransaction(() -> {
            worker.onAiScoringJob(rescore);
            return null;
        });

        AiEvaluation scored = reload(answer);
        assertThat(scored.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(scored.getFailureReason()).isNull();
        assertThat(scored.getRawScore()).isEqualTo(73); // 4+2+4+2+4+1+2 = 19 of 26 at Form 2 (250 words)
        assertThat(scored.getRubricVersionPublicId()).isEqualTo(VERSION);
        assertThat(scored.getCriteriaScoresJson()).startsWith("{\"scale\":\"PEARSON_V1\",\"rubricVersion\":\""
                + VERSION + "\",\"traits\":{\"CONTENT\":4,");
        assertThat(scored.getDetailJson()).doesNotContain(essay());
    }

    private ScoringCommandService host(Instant now) {
        AiScoringDispatcher dispatcher = new AiScoringDispatcher(answerRepository, rabbitTemplate,
                Clock.fixed(now, ZoneOffset.UTC), new AiProviderProperties());
        return new ScoringCommandService(mock(ScoringIngestService.class), answerRepository,
                mock(ObjectiveScoringService.class), dispatcher, contentReader,
                mock(com.pte.session.SessionService.class), mock(ScorePublicationLockService.class),
                mock(ApplicationEventPublisher.class), com.pte.TestClocks.fixed());
    }

    private List<AiScoringJob> sentJobs(int expected) {
        ArgumentCaptor<AiScoringJob> jobs = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate, times(expected)).convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY), jobs.capture());
        return jobs.getAllValues();
    }

    private AiEvaluation reload(AiEvaluation answer) {
        return answerRepository.findByAnswerPublicId(answer.getAnswerPublicId()).orElseThrow();
    }

    private static GeminiResponse response(String json) {
        return new GeminiResponse(JSON.readTree(json), "gemini-test");
    }

    /** 250 words in ten-word sentences: Form 2. */
    private static String essay() {
        return ("essay ".repeat(10).trim() + ". ").repeat(25).trim();
    }

    private <T> T inTransaction(Supplier<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> callback.get());
    }
}
