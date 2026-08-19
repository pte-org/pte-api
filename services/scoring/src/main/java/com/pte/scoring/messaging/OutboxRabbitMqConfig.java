package com.pte.scoring.messaging;

import com.pte.scoring.constant.ScoringConstants;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ config for scoring's OUTBOX-RELAY concern (rabbitmq-outbox-migration
 * Phase 6): producer side (scoring's own outbox relay exchange) plus consumer
 * side (exam-delivery's AnswerSubmitted — ordering-sensitive — and
 * scheduling's ScoringRequested — not ordering-sensitive).
 *
 * <p><b>Deliberately separate from {@link com.pte.scoring.config.RabbitMqConfig}</b>
 * (scoring's pre-existing AI-vendor work queue, Milestone 1 Phase 9) — different
 * concern, different exchanges/queues, and critically DIFFERENT bean names:
 * that class already owns the default-named {@code rabbitListenerContainerFactory}
 * bean (used by {@code AiScoringWorker}), so every bean here uses an explicit,
 * distinct name to avoid a `BeanDefinitionOverrideException` and, more
 * importantly, to avoid accidentally wiring the AI-vendor queue's factory
 * (normal concurrency, unrelated retry policy) onto an ordering-sensitive
 * outbox-relay consumer or vice versa.
 *
 * <p><b>Ordering-critical:</b> {@link #answerIngestListenerContainerFactory}
 * pins concurrency to 1 explicitly (per plan.md's confirmed ordering
 * mitigation: single queue + concurrency=1, not a code-level sequencing
 * scheme) — {@link #scoringCommandListenerContainerFactory} does not, since
 * {@code ScoringCommandConsumer} handles one-shot host commands, not an
 * ordered per-attempt stream.
 */
@Configuration
public class OutboxRabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;
    private static final int ANSWER_INGEST_CONCURRENCY = 1;

    private static final String ANSWER_INGEST_DEAD_LETTER_EXCHANGE = ScoringConstants.QUEUE_ANSWER_INGEST + ".dlx";
    private static final String SCORING_COMMAND_DEAD_LETTER_EXCHANGE = ScoringConstants.QUEUE_SCORING_COMMAND + ".dlx";

    // --- Producer side: scoring's own outbox relay publishes here. ---

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(ScoringConstants.OUTBOX_EXCHANGE, true, false);
    }

    // --- Consumer side: exam-delivery's AnswerSubmitted (ordering-sensitive). ---

    @Bean
    public TopicExchange examDeliveryOutboxExchange() {
        return new TopicExchange(ScoringConstants.EXAMDELIVERY_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange answerIngestDeadLetterExchange() {
        return new DirectExchange(ANSWER_INGEST_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue answerIngestQueue() {
        return QueueBuilder.durable(ScoringConstants.QUEUE_ANSWER_INGEST)
                .withArgument("x-dead-letter-exchange", ANSWER_INGEST_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ScoringConstants.ANSWER_INGEST_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue answerIngestDeadLetterQueue() {
        return QueueBuilder.durable(ScoringConstants.QUEUE_ANSWER_INGEST_DLQ).build();
    }

    @Bean
    public Binding answerIngestBinding(Queue answerIngestQueue, TopicExchange examDeliveryOutboxExchange) {
        return BindingBuilder.bind(answerIngestQueue).to(examDeliveryOutboxExchange)
                .with(ScoringConstants.ANSWER_INGEST_ROUTING_PATTERN);
    }

    @Bean
    public Binding answerIngestDeadLetterBinding(Queue answerIngestDeadLetterQueue,
            DirectExchange answerIngestDeadLetterExchange) {
        return BindingBuilder.bind(answerIngestDeadLetterQueue).to(answerIngestDeadLetterExchange)
                .with(ScoringConstants.ANSWER_INGEST_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public RetryOperationsInterceptor answerIngestRetryInterceptor() {
        return retryInterceptor("Answer ingest retries exhausted");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory answerIngestListenerContainerFactory(
            ConnectionFactory connectionFactory, RetryOperationsInterceptor answerIngestRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(answerIngestRetryInterceptor);
        // Ordering-critical: do not raise without re-verifying per-attempt
        // answer-ingestion ordering (see class Javadoc).
        factory.setConcurrentConsumers(ANSWER_INGEST_CONCURRENCY);
        factory.setMaxConcurrentConsumers(ANSWER_INGEST_CONCURRENCY);
        return factory;
    }

    // --- Consumer side: scheduling's ScoringRequested (not ordering-sensitive). ---

    @Bean
    public TopicExchange schedulingOutboxExchange() {
        return new TopicExchange(ScoringConstants.SCHEDULING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange scoringCommandDeadLetterExchange() {
        return new DirectExchange(SCORING_COMMAND_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue scoringCommandQueue() {
        return QueueBuilder.durable(ScoringConstants.QUEUE_SCORING_COMMAND)
                .withArgument("x-dead-letter-exchange", SCORING_COMMAND_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ScoringConstants.SCORING_COMMAND_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue scoringCommandDeadLetterQueue() {
        return QueueBuilder.durable(ScoringConstants.QUEUE_SCORING_COMMAND_DLQ).build();
    }

    @Bean
    public Binding scoringCommandBinding(Queue scoringCommandQueue, TopicExchange schedulingOutboxExchange) {
        return BindingBuilder.bind(scoringCommandQueue).to(schedulingOutboxExchange)
                .with(ScoringConstants.SCORING_COMMAND_ROUTING_PATTERN);
    }

    @Bean
    public Binding scoringCommandDeadLetterBinding(Queue scoringCommandDeadLetterQueue,
            DirectExchange scoringCommandDeadLetterExchange) {
        return BindingBuilder.bind(scoringCommandDeadLetterQueue).to(scoringCommandDeadLetterExchange)
                .with(ScoringConstants.SCORING_COMMAND_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public RetryOperationsInterceptor scoringCommandRetryInterceptor() {
        return retryInterceptor("Scoring command processing retries exhausted");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory scoringCommandListenerContainerFactory(
            ConnectionFactory connectionFactory, RetryOperationsInterceptor scoringCommandRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(scoringCommandRetryInterceptor);
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor(String exhaustedMessage) {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException(exhaustedMessage, cause);
        };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_INTERVAL_MS, MULTIPLIER, MAX_INTERVAL_MS)
                .recoverer(recoverer)
                .build();
    }
}
