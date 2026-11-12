package com.pte.practice.internal.config;

import com.pte.practice.internal.constant.PracticeConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Practice scoring work queue, shaped like the exam's ({@code ScoringRabbitMqConfig}): no container retry, because
 * redelivering a job would call the vendor again and pay twice; a rejected message goes to the DLQ, where the worker
 * marks the item FAILED. A few consumers ({@code practice.scoring.concurrency}) let a scored mock test finish in
 * minutes; each job holds one recording, so the count stays small for the 512 MB container.
 */
@Configuration
@EnableConfigurationProperties(PracticeScoringProperties.class)
public class PracticeScoringRabbitMqConfig {

    @Bean
    public DirectExchange practiceScoringExchange() {
        return new DirectExchange(PracticeConstants.PRACTICE_SCORING_EXCHANGE);
    }

    @Bean
    public DirectExchange practiceScoringDeadLetterExchange() {
        return new DirectExchange(PracticeConstants.PRACTICE_SCORING_DLX);
    }

    @Bean
    public Queue practiceScoringQueue() {
        return QueueBuilder.durable(PracticeConstants.PRACTICE_SCORING_QUEUE)
                .withArgument("x-dead-letter-exchange", PracticeConstants.PRACTICE_SCORING_DLX)
                .withArgument("x-dead-letter-routing-key", PracticeConstants.PRACTICE_SCORING_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue practiceScoringDeadLetterQueue() {
        return QueueBuilder.durable(PracticeConstants.PRACTICE_SCORING_DLQ).build();
    }

    @Bean
    public Binding practiceScoringBinding(Queue practiceScoringQueue, DirectExchange practiceScoringExchange) {
        return BindingBuilder.bind(practiceScoringQueue).to(practiceScoringExchange)
                .with(PracticeConstants.PRACTICE_SCORING_ROUTING_KEY);
    }

    @Bean
    public Binding practiceScoringDeadLetterBinding(Queue practiceScoringDeadLetterQueue,
            DirectExchange practiceScoringDeadLetterExchange) {
        return BindingBuilder.bind(practiceScoringDeadLetterQueue).to(practiceScoringDeadLetterExchange)
                .with(PracticeConstants.PRACTICE_SCORING_ROUTING_KEY);
    }

    /** Serves both the work queue and its DLQ listener. */
    @Bean
    public SimpleRabbitListenerContainerFactory practiceScoringRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter,
            PracticeScoringProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
