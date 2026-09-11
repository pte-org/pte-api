package com.pte.authoring.messaging;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.OutboxEntry;
import com.pte.authoring.repository.OutboxRepository;
import com.pte.common.messaging.AbstractOutboxRelay;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls authoring's outbox and publishes to {@link AuthoringConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class AuthoringOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public AuthoringOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, AuthoringConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
