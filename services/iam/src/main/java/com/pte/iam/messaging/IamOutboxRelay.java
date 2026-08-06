package com.pte.iam.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.OutboxEntry;
import com.pte.iam.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls iam's outbox and publishes to {@link IamConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class IamOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public IamOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, IamConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
