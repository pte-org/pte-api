package com.pte.proctor.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.proctor.constant.ProctorConstants;
import com.pte.proctor.domain.OutboxEntry;
import com.pte.proctor.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls proctor's outbox and publishes to {@link ProctorConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class ProctorOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public ProctorOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, ProctorConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
