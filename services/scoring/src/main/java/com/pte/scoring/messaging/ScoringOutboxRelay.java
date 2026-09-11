package com.pte.scoring.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.OutboxEntry;
import com.pte.scoring.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls scoring's outbox and publishes to {@link ScoringConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class ScoringOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public ScoringOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, ScoringConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
