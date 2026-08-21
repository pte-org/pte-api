package com.pte.reporting.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.reporting.constant.ReportingConstants;
import com.pte.reporting.domain.OutboxEntry;
import com.pte.reporting.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/** Polls reporting's outbox and publishes to {@link ReportingConstants#OUTBOX_EXCHANGE} (ADR-002 supersession). */
@Component
public class ReportingOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public ReportingOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, ReportingConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
