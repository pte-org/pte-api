package com.pte.admin.messaging;

import com.pte.admin.constant.AdminConstants;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ configuration for Admin's outbox publisher and inbound IAM user
 * events. The inbound path has its own listener factory, retry advice,
 * durable queue, and dead-letter queue so it cannot alter the producer path.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(AdminConstants.OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange iamOutboxExchange() {
        return new TopicExchange(AdminConstants.IAM_OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange userEventsDeadLetterExchange() {
        return new DirectExchange(AdminConstants.QUEUE_USER_EVENTS + ".dlx");
    }

    @Bean
    public Queue userEventsQueue() {
        return QueueBuilder.durable(AdminConstants.QUEUE_USER_EVENTS)
                .withArgument("x-dead-letter-exchange", AdminConstants.QUEUE_USER_EVENTS + ".dlx")
                .withArgument("x-dead-letter-routing-key", AdminConstants.USER_EVENTS_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue userEventsDeadLetterQueue() {
        return QueueBuilder.durable(AdminConstants.QUEUE_USER_EVENTS_DLQ).build();
    }

    @Bean
    public Binding userEventsBinding(Queue userEventsQueue, TopicExchange iamOutboxExchange) {
        return BindingBuilder.bind(userEventsQueue).to(iamOutboxExchange)
                .with(AdminConstants.USER_EVENTS_ROUTING_PATTERN);
    }

    @Bean
    public Binding userEventsDeadLetterBinding(Queue userEventsDeadLetterQueue,
            DirectExchange userEventsDeadLetterExchange) {
        return BindingBuilder.bind(userEventsDeadLetterQueue).to(userEventsDeadLetterExchange)
                .with(AdminConstants.USER_EVENTS_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // Typed listeners use the declared method parameter type. This also
        // preserves the existing producer-side converter behavior.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RetryOperationsInterceptor userEventsRetryInterceptor() {
        MethodInvocationRecoverer<Object> recoverer = (args, cause) -> {
            throw new AmqpRejectAndDontRequeueException("User event processing retries exhausted", cause);
        };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2_000L, 2.0, 10_000L)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory eventBackboneListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor userEventsRetryInterceptor,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(userEventsRetryInterceptor);
        return factory;
    }
}
