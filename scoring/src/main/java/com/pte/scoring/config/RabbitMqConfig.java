package com.pte.scoring.config;

import com.pte.scoring.constant.ScoringConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;

/**
 * AI scoring work queue (Phase 9 activates what Phase 7 deliberately deferred
 * — objective scoring was fast/synchronous, needed no queue; a vendor call is
 * slow/unreliable and needs bounded retry + a DLQ). {@code AI_SCORING_QUEUE}
 * dead-letters to {@code AI_SCORING_DLQ} after 3 failed delivery attempts
 * (stateless retry interceptor + reject-and-dont-requeue recoverer) — a stuck
 * job lands somewhere host-visible ({@code ScoringAnswerStatus.SCORING_FAILED}),
 * never retries forever.
 */
@Configuration
public class RabbitMqConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor aiScoringRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_INTERVAL_MS, MULTIPLIER, MAX_INTERVAL_MS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor aiScoringRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(aiScoringRetryInterceptor);
        return factory;
    }
}
