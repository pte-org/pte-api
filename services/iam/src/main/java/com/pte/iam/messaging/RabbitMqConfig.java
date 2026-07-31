package com.pte.iam.messaging;

import com.pte.iam.constant.IamConstants;
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
 * RabbitMQ config for iam (rabbitmq-outbox-migration Phase 3): producer side
 * (iam's own outbox relay exchange) plus consumer side (a queue bound to
 * admin's outbox exchange for Tenant events), retry/DLQ shaped like
 * notification's {@code EMAIL_QUEUE} (3 attempts, exponential backoff,
 * dead-letter on exhaustion).
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

    private static final String TENANT_EVENTS_DEAD_LETTER_EXCHANGE = IamConstants.QUEUE_TENANT_EVENTS + ".dlx";

    // --- Producer side: iam's own outbox relay publishes here. ---

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(IamConstants.OUTBOX_EXCHANGE, true, false);
    }

    // --- Consumer side: admin's Tenant events. ---

    @Bean
    public TopicExchange adminOutboxExchange() {
        return new TopicExchange(IamConstants.ADMIN_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange tenantEventsDeadLetterExchange() {
        return new DirectExchange(TENANT_EVENTS_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue tenantEventsQueue() {
        return QueueBuilder.durable(IamConstants.QUEUE_TENANT_EVENTS)
                .withArgument("x-dead-letter-exchange", TENANT_EVENTS_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", IamConstants.TENANT_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue tenantEventsDeadLetterQueue() {
        return QueueBuilder.durable(IamConstants.QUEUE_TENANT_EVENTS_DLQ).build();
    }

    @Bean
    public Binding tenantEventsBinding(Queue tenantEventsQueue, TopicExchange adminOutboxExchange) {
        return BindingBuilder.bind(tenantEventsQueue).to(adminOutboxExchange)
                .with(IamConstants.TENANT_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding tenantEventsDeadLetterBinding(Queue tenantEventsDeadLetterQueue,
            DirectExchange tenantEventsDeadLetterExchange) {
        return BindingBuilder.bind(tenantEventsDeadLetterQueue).to(tenantEventsDeadLetterExchange)
                .with(IamConstants.TENANT_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor tenantEventsRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Tenant event processing retries exhausted", cause);
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
            RetryOperationsInterceptor tenantEventsRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(tenantEventsRetryInterceptor);
        return factory;
    }
}
