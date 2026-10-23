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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Durable work queue for practice explanations; disabled when Gemini is disabled. */
@Configuration
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_AI_ENABLED_PROPERTY, havingValue = "true")
public class PracticeExplanationRabbitMqConfig {

    private static final String DEAD_LETTER_EXCHANGE = PracticeConstants.PRACTICE_EXPLANATION_EXCHANGE + ".dlx";

    @Bean
    public DirectExchange practiceExplanationExchange() {
        return new DirectExchange(PracticeConstants.PRACTICE_EXPLANATION_EXCHANGE);
    }

    @Bean
    public DirectExchange practiceExplanationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue practiceExplanationQueue() {
        return QueueBuilder.durable(PracticeConstants.PRACTICE_EXPLANATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PracticeConstants.PRACTICE_EXPLANATION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue practiceExplanationDeadLetterQueue() {
        return QueueBuilder.durable(PracticeConstants.PRACTICE_EXPLANATION_DLQ).build();
    }

    @Bean
    public Binding practiceExplanationBinding(Queue practiceExplanationQueue,
            DirectExchange practiceExplanationExchange) {
        return BindingBuilder.bind(practiceExplanationQueue).to(practiceExplanationExchange)
                .with(PracticeConstants.PRACTICE_EXPLANATION_ROUTING_KEY);
    }

    @Bean
    public Binding practiceExplanationDeadLetterBinding(Queue practiceExplanationDeadLetterQueue,
            DirectExchange practiceExplanationDeadLetterExchange) {
        return BindingBuilder.bind(practiceExplanationDeadLetterQueue).to(practiceExplanationDeadLetterExchange)
                .with(PracticeConstants.PRACTICE_EXPLANATION_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory practiceExplanationRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
