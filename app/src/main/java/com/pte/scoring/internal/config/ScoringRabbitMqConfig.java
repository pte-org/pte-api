package com.pte.scoring.internal.config;

import com.pte.scoring.internal.constant.ScoringConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI scoring work queue — a vendor call is slow and unreliable, unlike
 * objective scoring's synchronous path. There is no container-level retry:
 * the worker's vendor clients retry each HTTP call themselves
 * ({@code ProviderGuard}), so redelivering the whole job would only call every
 * vendor again and pay twice. A listener that throws has its message rejected
 * without requeue, and the queue's {@code x-dead-letter-exchange} routes it
 * to {@code AI_SCORING_DLQ}, where the worker marks the answer
 * {@code SCORING_FAILED} so a host sees it instead of it retrying forever.
 */
@Configuration
public class ScoringRabbitMqConfig {

    // Two jobs at a time keeps the 512 MB app container and the vendors' rate limits comfortable.
    private static final int WORK_QUEUE_CONSUMERS = 2;
    private static final int DEAD_LETTER_CONSUMERS = 1;

    private static final String DEAD_LETTER_EXCHANGE = ScoringConstants.AI_SCORING_EXCHANGE + ".dlx";

    @Bean
    public DirectExchange aiScoringExchange() {
        return new DirectExchange(ScoringConstants.AI_SCORING_EXCHANGE);
    }

    @Bean
    public DirectExchange aiScoringDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue aiScoringQueue() {
        return QueueBuilder.durable(ScoringConstants.AI_SCORING_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ScoringConstants.AI_SCORING_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue aiScoringDeadLetterQueue() {
        return QueueBuilder.durable(ScoringConstants.AI_SCORING_DLQ).build();
    }

    @Bean
    public Binding aiScoringBinding(Queue aiScoringQueue, DirectExchange aiScoringExchange) {
        return BindingBuilder.bind(aiScoringQueue).to(aiScoringExchange).with(ScoringConstants.AI_SCORING_ROUTING_KEY);
    }

    @Bean
    public Binding aiScoringDeadLetterBinding(Queue aiScoringDeadLetterQueue, DirectExchange aiScoringDeadLetterExchange) {
        return BindingBuilder.bind(aiScoringDeadLetterQueue).to(aiScoringDeadLetterExchange)
                .with(ScoringConstants.AI_SCORING_ROUTING_KEY);
    }

    // Named per-module (not the bare "rabbitListenerContainerFactory" every
    // @RabbitListener in this codebase used to default to when it lived in its
    // own microservice) — notification defines its own same-shaped factory,
    // and two @Bean methods of the same name/type in one Spring context is a
    // collision, not an override. Every @RabbitListener in this module must
    // reference one of these factory names. MessageConverter is the one shared
    // bean from com.pte.shared.config.RabbitMessageConverterConfig — resolved
    // by type, unambiguous since only one MessageConverter bean exists.
    @Bean
    public SimpleRabbitListenerContainerFactory scoringRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        return containerFactory(connectionFactory, jsonMessageConverter, WORK_QUEUE_CONSUMERS);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory scoringDeadLetterContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        return containerFactory(connectionFactory, jsonMessageConverter, DEAD_LETTER_CONSUMERS);
    }

    private static SimpleRabbitListenerContainerFactory containerFactory(ConnectionFactory connectionFactory,
            MessageConverter messageConverter, int consumers) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(consumers);
        factory.setMaxConcurrentConsumers(consumers);
        // A job holds a consumer for up to a minute and a half; don't park more behind it.
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
