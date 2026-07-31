package com.pte.scheduling.messaging;

import com.pte.scheduling.constant.SchedulingConstants;
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
 * RabbitMQ config for scheduling (rabbitmq-outbox-migration Phase 4): producer
 * side (scheduling's own outbox relay exchange) plus consumer side (a queue
 * bound to authoring's outbox exchange for ExamSnapshot events), retry/DLQ
 * shaped like notification's {@code EMAIL_QUEUE} — same pattern established
 * in iam's Phase 3 config.
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

    private static final String SNAPSHOT_EVENTS_DEAD_LETTER_EXCHANGE = SchedulingConstants.QUEUE_SNAPSHOT_EVENTS + ".dlx";

    // --- Producer side: scheduling's own outbox relay publishes here. ---

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(SchedulingConstants.OUTBOX_EXCHANGE, true, false);
    }

    // --- Consumer side: authoring's ExamSnapshot events. ---

    @Bean
    public TopicExchange authoringOutboxExchange() {
        return new TopicExchange(SchedulingConstants.AUTHORING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange snapshotEventsDeadLetterExchange() {
        return new DirectExchange(SNAPSHOT_EVENTS_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue snapshotEventsQueue() {
        return QueueBuilder.durable(SchedulingConstants.QUEUE_SNAPSHOT_EVENTS)
                .withArgument("x-dead-letter-exchange", SNAPSHOT_EVENTS_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SchedulingConstants.SNAPSHOT_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue snapshotEventsDeadLetterQueue() {
        return QueueBuilder.durable(SchedulingConstants.QUEUE_SNAPSHOT_EVENTS_DLQ).build();
    }

    @Bean
    public Binding snapshotEventsBinding(Queue snapshotEventsQueue, TopicExchange authoringOutboxExchange) {
        return BindingBuilder.bind(snapshotEventsQueue).to(authoringOutboxExchange)
                .with(SchedulingConstants.SNAPSHOT_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding snapshotEventsDeadLetterBinding(Queue snapshotEventsDeadLetterQueue,
            DirectExchange snapshotEventsDeadLetterExchange) {
        return BindingBuilder.bind(snapshotEventsDeadLetterQueue).to(snapshotEventsDeadLetterExchange)
                .with(SchedulingConstants.SNAPSHOT_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor snapshotEventsRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Snapshot event processing retries exhausted", cause);
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
            RetryOperationsInterceptor snapshotEventsRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(snapshotEventsRetryInterceptor);
        return factory;
    }
}
