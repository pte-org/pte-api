package com.pte.notification.config;

import com.pte.notification.constant.NotificationConstants;
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
 * Email send work queue — same shape as scoring's Phase 9 AI-scoring queue.
 * SMTP is exactly the "slow/unreliable external call" ADR-002 designed the
 * Kafka/RabbitMQ split around. {@code EMAIL_QUEUE} dead-letters to {@code
 * EMAIL_DLQ} after 3 failed delivery attempts — the recoverer throws {@link
 * AmqpRejectAndDontRequeueException} once retries are exhausted, which the
 * container turns into a NACK-without-requeue, routed to the DLX by the
 * queue's {@code x-dead-letter-exchange}. A stuck job lands somewhere
 * host-visible ({@code NotificationStatus.FAILED}), never retries forever.
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

    private static final String DEAD_LETTER_EXCHANGE = NotificationConstants.EMAIL_EXCHANGE + ".dlx";

    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(NotificationConstants.EMAIL_EXCHANGE);
    }

    @Bean
    public DirectExchange emailDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(NotificationConstants.EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NotificationConstants.EMAIL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(NotificationConstants.EMAIL_DLQ).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(NotificationConstants.EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding emailDeadLetterBinding(Queue emailDeadLetterQueue, DirectExchange emailDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(emailDeadLetterExchange)
                .with(NotificationConstants.EMAIL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor emailRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Email send retries exhausted", cause);
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
            RetryOperationsInterceptor emailRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(emailRetryInterceptor);
        return factory;
    }

    // ============================================================================
    // Event-backbone consumers (rabbitmq-outbox-migration Phase 8): 4 queues, one
    // per producer (iam, scheduling, reporting, proctor). Deliberately separate
    // from the email work queue above — different concern (event-backbone
    // consumption vs. an internal SMTP-retry work queue), same separation
    // principle as scoring's Phase 6 treatment. Uses its own retry interceptor
    // and a distinctly-named listener container factory
    // (`eventBackboneListenerContainerFactory`, NOT the default-named
    // `rabbitListenerContainerFactory` above, which is EMAIL_QUEUE's) — every
    // one of the four `@RabbitListener`s below must reference it explicitly via
    // `containerFactory = "eventBackboneListenerContainerFactory"`.
    // ============================================================================

    @Bean
    public TopicExchange iamOutboxExchange() {
        return new TopicExchange(NotificationConstants.IAM_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange schedulingOutboxExchange() {
        return new TopicExchange(NotificationConstants.SCHEDULING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange reportingOutboxExchange() {
        return new TopicExchange(NotificationConstants.REPORTING_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange proctorOutboxExchange() {
        return new TopicExchange(NotificationConstants.PROCTOR_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange userEventsDeadLetterExchange() {
        return new DirectExchange(NotificationConstants.QUEUE_USER_EVENTS + ".dlx");
    }

    @Bean
    public Queue userEventsQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_USER_EVENTS)
                .withArgument("x-dead-letter-exchange", NotificationConstants.QUEUE_USER_EVENTS + ".dlx")
                .withArgument("x-dead-letter-routing-key", NotificationConstants.USER_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue userEventsDeadLetterQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_USER_EVENTS_DLQ).build();
    }

    @Bean
    public Binding userEventsBinding(Queue userEventsQueue, TopicExchange iamOutboxExchange) {
        return BindingBuilder.bind(userEventsQueue).to(iamOutboxExchange)
                .with(NotificationConstants.USER_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding userEventsDeadLetterBinding(Queue userEventsDeadLetterQueue,
            DirectExchange userEventsDeadLetterExchange) {
        return BindingBuilder.bind(userEventsDeadLetterQueue).to(userEventsDeadLetterExchange)
                .with(NotificationConstants.USER_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange sessionEventsDeadLetterExchange() {
        return new DirectExchange(NotificationConstants.QUEUE_SESSION_EVENTS + ".dlx");
    }

    @Bean
    public Queue sessionEventsQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_SESSION_EVENTS)
                .withArgument("x-dead-letter-exchange", NotificationConstants.QUEUE_SESSION_EVENTS + ".dlx")
                .withArgument("x-dead-letter-routing-key", NotificationConstants.SESSION_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue sessionEventsDeadLetterQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_SESSION_EVENTS_DLQ).build();
    }

    @Bean
    public Binding sessionEventsBinding(Queue sessionEventsQueue, TopicExchange schedulingOutboxExchange) {
        return BindingBuilder.bind(sessionEventsQueue).to(schedulingOutboxExchange)
                .with(NotificationConstants.SESSION_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding sessionEventsDeadLetterBinding(Queue sessionEventsDeadLetterQueue,
            DirectExchange sessionEventsDeadLetterExchange) {
        return BindingBuilder.bind(sessionEventsDeadLetterQueue).to(sessionEventsDeadLetterExchange)
                .with(NotificationConstants.SESSION_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange attemptReportEventsDeadLetterExchange() {
        return new DirectExchange(NotificationConstants.QUEUE_ATTEMPT_REPORT_EVENTS + ".dlx");
    }

    @Bean
    public Queue attemptReportEventsQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_ATTEMPT_REPORT_EVENTS)
                .withArgument("x-dead-letter-exchange", NotificationConstants.QUEUE_ATTEMPT_REPORT_EVENTS + ".dlx")
                .withArgument("x-dead-letter-routing-key", NotificationConstants.ATTEMPT_REPORT_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue attemptReportEventsDeadLetterQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_ATTEMPT_REPORT_EVENTS_DLQ).build();
    }

    @Bean
    public Binding attemptReportEventsBinding(Queue attemptReportEventsQueue, TopicExchange reportingOutboxExchange) {
        return BindingBuilder.bind(attemptReportEventsQueue).to(reportingOutboxExchange)
                .with(NotificationConstants.ATTEMPT_REPORT_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding attemptReportEventsDeadLetterBinding(Queue attemptReportEventsDeadLetterQueue,
            DirectExchange attemptReportEventsDeadLetterExchange) {
        return BindingBuilder.bind(attemptReportEventsDeadLetterQueue).to(attemptReportEventsDeadLetterExchange)
                .with(NotificationConstants.ATTEMPT_REPORT_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange violationEventsDeadLetterExchange() {
        return new DirectExchange(NotificationConstants.QUEUE_VIOLATION_EVENTS + ".dlx");
    }

    @Bean
    public Queue violationEventsQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_VIOLATION_EVENTS)
                .withArgument("x-dead-letter-exchange", NotificationConstants.QUEUE_VIOLATION_EVENTS + ".dlx")
                .withArgument("x-dead-letter-routing-key", NotificationConstants.VIOLATION_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue violationEventsDeadLetterQueue() {
        return QueueBuilder.durable(NotificationConstants.QUEUE_VIOLATION_EVENTS_DLQ).build();
    }

    @Bean
    public Binding violationEventsBinding(Queue violationEventsQueue, TopicExchange proctorOutboxExchange) {
        return BindingBuilder.bind(violationEventsQueue).to(proctorOutboxExchange)
                .with(NotificationConstants.VIOLATION_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding violationEventsDeadLetterBinding(Queue violationEventsDeadLetterQueue,
            DirectExchange violationEventsDeadLetterExchange) {
        return BindingBuilder.bind(violationEventsDeadLetterQueue).to(violationEventsDeadLetterExchange)
                .with(NotificationConstants.VIOLATION_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public RetryOperationsInterceptor eventBackboneRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("Event-backbone consumer retries exhausted", cause);
        };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_INTERVAL_MS, MULTIPLIER, MAX_INTERVAL_MS)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory eventBackboneListenerContainerFactory(
            ConnectionFactory connectionFactory, RetryOperationsInterceptor eventBackboneRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(eventBackboneRetryInterceptor);
        return factory;
    }
}
