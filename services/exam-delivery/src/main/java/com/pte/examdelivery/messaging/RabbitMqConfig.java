package com.pte.examdelivery.messaging;

import com.pte.examdelivery.constant.ExamDeliveryConstants;
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
 * RabbitMQ config for exam-delivery (rabbitmq-outbox-migration Phase 5):
 * producer side (exam-delivery's own outbox relay exchange) plus consumer
 * side (a SINGLE durable queue bound to proctor's outbox exchange for
 * ProctorCommand events).
 *
 * <p><b>Ordering-critical:</b> {@link #rabbitListenerContainerFactory} pins
 * {@code concurrentConsumers}/{@code maxConcurrentConsumers} to 1 explicitly
 * — even though Spring AMQP's own default happens to also be 1, this is
 * pinned deliberately and documented so a future throughput tweak doesn't
 * silently break per-attempt command ordering (plan.md's confirmed ordering
 * mitigation: single queue + concurrency=1, not a code-level sequencing
 * scheme).
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;
    private static final int PROCTOR_COMMANDS_CONCURRENCY = 1;

    private static final String PROCTOR_COMMANDS_DEAD_LETTER_EXCHANGE =
            ExamDeliveryConstants.QUEUE_PROCTOR_COMMANDS + ".dlx";

    // --- Producer side: exam-delivery's own outbox relay publishes here. ---

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(ExamDeliveryConstants.OUTBOX_EXCHANGE, true, false);
    }

    // --- Consumer side: proctor's ProctorCommand events (ordering-sensitive). ---

    @Bean
    public TopicExchange proctorOutboxExchange() {
        return new TopicExchange(ExamDeliveryConstants.PROCTOR_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange proctorCommandsDeadLetterExchange() {
        return new DirectExchange(PROCTOR_COMMANDS_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue proctorCommandsQueue() {
        return QueueBuilder.durable(ExamDeliveryConstants.QUEUE_PROCTOR_COMMANDS)
                .withArgument("x-dead-letter-exchange", PROCTOR_COMMANDS_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ExamDeliveryConstants.PROCTOR_COMMANDS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue proctorCommandsDeadLetterQueue() {
        return QueueBuilder.durable(ExamDeliveryConstants.QUEUE_PROCTOR_COMMANDS_DLQ).build();
    }

    @Bean
    public Binding proctorCommandsBinding(Queue proctorCommandsQueue, TopicExchange proctorOutboxExchange) {
        return BindingBuilder.bind(proctorCommandsQueue).to(proctorOutboxExchange)
                .with(ExamDeliveryConstants.PROCTOR_COMMANDS_ROUTING_PATTERN);
    }

    @Bean
    public Binding proctorCommandsDeadLetterBinding(Queue proctorCommandsDeadLetterQueue,
            DirectExchange proctorCommandsDeadLetterExchange) {
        return BindingBuilder.bind(proctorCommandsDeadLetterQueue).to(proctorCommandsDeadLetterExchange)
                .with(ExamDeliveryConstants.PROCTOR_COMMANDS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor proctorCommandsRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Proctor command processing retries exhausted", cause);
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
            RetryOperationsInterceptor proctorCommandsRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(proctorCommandsRetryInterceptor);
        // Ordering-critical: do not raise without re-verifying per-attempt
        // ProctorCommand ordering (see class Javadoc).
        factory.setConcurrentConsumers(PROCTOR_COMMANDS_CONCURRENCY);
        factory.setMaxConcurrentConsumers(PROCTOR_COMMANDS_CONCURRENCY);
        return factory;
    }
}
