package com.pte.scoring.internal.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScoringRabbitMqConfigTest {

    private final ScoringRabbitMqConfig config = new ScoringRabbitMqConfig();
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final MessageConverter messageConverter = mock(MessageConverter.class);

    @Test
    void workQueueRunsTwoJobsAtATimeOneMessageEachAndNeverRequeues() {
        SimpleRabbitListenerContainerFactory factory =
                config.scoringRabbitListenerContainerFactory(connectionFactory, messageConverter);

        assertThat(field(factory, "concurrentConsumers")).isEqualTo(2);
        assertThat(field(factory, "maxConcurrentConsumers")).isEqualTo(2);
        assertCommonSettings(factory);
    }

    @Test
    void deadLetterQueueIsDrainedByASingleConsumer() {
        SimpleRabbitListenerContainerFactory factory =
                config.scoringDeadLetterContainerFactory(connectionFactory, messageConverter);

        assertThat(field(factory, "concurrentConsumers")).isEqualTo(1);
        assertThat(field(factory, "maxConcurrentConsumers")).isEqualTo(1);
        assertCommonSettings(factory);
    }

    // Retries happen per vendor call inside the worker (ProviderGuard); a container-level retry would
    // call every vendor again and multiply the paid calls.
    private void assertCommonSettings(SimpleRabbitListenerContainerFactory factory) {
        assertThat(field(factory, "prefetchCount")).isEqualTo(1);
        assertThat(field(factory, "defaultRequeueRejected")).isEqualTo(false);
        assertThat(field(factory, "adviceChain")).isNull();
        assertThat(field(factory, "messageConverter")).isSameAs(messageConverter);
    }

    private static Object field(SimpleRabbitListenerContainerFactory factory, String name) {
        return ReflectionTestUtils.getField(factory, name);
    }
}
