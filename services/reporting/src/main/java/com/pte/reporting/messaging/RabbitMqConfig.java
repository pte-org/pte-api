package com.pte.reporting.messaging;

import com.pte.reporting.constant.ReportingConstants;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ config for reporting (rabbitmq-outbox-migration Phase 7): producer
 * side (reporting's own outbox relay exchange) plus THREE consumer sides —
 * exam-delivery's attempt/answer events, scoring's AnswerScored, and
 * scheduling's PublishRequested — each its own queue + DLQ, mirroring the
 * three separate Kafka topics reporting consumed from before. None of the
 * three are ordering-sensitive (Phase 7 Design Constraints), so a single
 * shared retry interceptor + default-named listener container factory
 * (normal concurrency) covers all three, unlike Phase 5/6's dedicated
 * concurrency=1 factories.
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

    private static final String ATTEMPT_INGEST_DEAD_LETTER_EXCHANGE = ReportingConstants.QUEUE_ATTEMPT_INGEST + ".dlx";
    private static final String ANSWER_SCORED_DEAD_LETTER_EXCHANGE = ReportingConstants.QUEUE_ANSWER_SCORED + ".dlx";
    private static final String PUBLISH_DEAD_LETTER_EXCHANGE = ReportingConstants.QUEUE_PUBLISH + ".dlx";

    // --- Producer side: reporting's own outbox relay publishes here. ---

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(ReportingConstants.OUTBOX_EXCHANGE, true, false);
    }

    // --- Consumer side 1: exam-delivery's ExamAttempt events. ---

    @Bean
    public TopicExchange examDeliveryOutboxExchange() {
        return new TopicExchange(ReportingConstants.EXAMDELIVERY_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange attemptIngestDeadLetterExchange() {
        return new DirectExchange(ATTEMPT_INGEST_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue attemptIngestQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_ATTEMPT_INGEST)
                .withArgument("x-dead-letter-exchange", ATTEMPT_INGEST_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ReportingConstants.ATTEMPT_INGEST_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue attemptIngestDeadLetterQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_ATTEMPT_INGEST_DLQ).build();
    }

    @Bean
    public Binding attemptIngestBinding(Queue attemptIngestQueue, TopicExchange examDeliveryOutboxExchange) {
        return BindingBuilder.bind(attemptIngestQueue).to(examDeliveryOutboxExchange)
                .with(ReportingConstants.ATTEMPT_INGEST_ROUTING_PATTERN);
    }

    @Bean
    public Binding attemptIngestDeadLetterBinding(Queue attemptIngestDeadLetterQueue,
            DirectExchange attemptIngestDeadLetterExchange) {
        return BindingBuilder.bind(attemptIngestDeadLetterQueue).to(attemptIngestDeadLetterExchange)
                .with(ReportingConstants.ATTEMPT_INGEST_DEAD_LETTER_ROUTING_KEY);
    }

    // --- Consumer side 2: scoring's ScoringAnswer events. ---

    @Bean
    public TopicExchange scoringOutboxExchange() {
        return new TopicExchange(ReportingConstants.SCORING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange answerScoredDeadLetterExchange() {
        return new DirectExchange(ANSWER_SCORED_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue answerScoredQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_ANSWER_SCORED)
                .withArgument("x-dead-letter-exchange", ANSWER_SCORED_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ReportingConstants.ANSWER_SCORED_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue answerScoredDeadLetterQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_ANSWER_SCORED_DLQ).build();
    }

    @Bean
    public Binding answerScoredBinding(Queue answerScoredQueue, TopicExchange scoringOutboxExchange) {
        return BindingBuilder.bind(answerScoredQueue).to(scoringOutboxExchange)
                .with(ReportingConstants.ANSWER_SCORED_ROUTING_PATTERN);
    }

    @Bean
    public Binding answerScoredDeadLetterBinding(Queue answerScoredDeadLetterQueue,
            DirectExchange answerScoredDeadLetterExchange) {
        return BindingBuilder.bind(answerScoredDeadLetterQueue).to(answerScoredDeadLetterExchange)
                .with(ReportingConstants.ANSWER_SCORED_DEAD_LETTER_ROUTING_KEY);
    }

    // --- Consumer side 3: scheduling's ExamSession events. ---

    @Bean
    public TopicExchange schedulingOutboxExchange() {
        return new TopicExchange(ReportingConstants.SCHEDULING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange publishDeadLetterExchange() {
        return new DirectExchange(PUBLISH_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue publishQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_PUBLISH)
                .withArgument("x-dead-letter-exchange", PUBLISH_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ReportingConstants.PUBLISH_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue publishDeadLetterQueue() {
        return QueueBuilder.durable(ReportingConstants.QUEUE_PUBLISH_DLQ).build();
    }

    @Bean
    public Binding publishBinding(Queue publishQueue, TopicExchange schedulingOutboxExchange) {
        return BindingBuilder.bind(publishQueue).to(schedulingOutboxExchange)
                .with(ReportingConstants.PUBLISH_ROUTING_PATTERN);
    }

    @Bean
    public Binding publishDeadLetterBinding(Queue publishDeadLetterQueue, DirectExchange publishDeadLetterExchange) {
        return BindingBuilder.bind(publishDeadLetterQueue).to(publishDeadLetterExchange)
                .with(ReportingConstants.PUBLISH_DEAD_LETTER_ROUTING_KEY);
    }

    // --- Shared consumer plumbing (none of the three queues is ordering-sensitive). ---

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor reportingConsumerRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Reporting consumer retries exhausted", cause);
        };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_INTERVAL_MS, MULTIPLIER, MAX_INTERVAL_MS)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor reportingConsumerRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(reportingConsumerRetryInterceptor);
        return factory;
    }
}
