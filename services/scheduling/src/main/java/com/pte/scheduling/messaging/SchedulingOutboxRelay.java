package com.pte.scheduling.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.OutboxEntry;
import com.pte.scheduling.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls scheduling's outbox and publishes to {@link SchedulingConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class SchedulingOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public SchedulingOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, SchedulingConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
