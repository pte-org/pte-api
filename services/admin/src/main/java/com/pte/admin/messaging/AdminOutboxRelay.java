package com.pte.admin.messaging;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.OutboxEntry;
import com.pte.admin.repository.OutboxRepository;
import com.pte.common.messaging.AbstractOutboxRelay;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls admin's outbox and publishes to {@link AdminConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class AdminOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public AdminOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, AdminConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
