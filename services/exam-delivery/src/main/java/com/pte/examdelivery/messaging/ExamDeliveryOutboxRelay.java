package com.pte.examdelivery.messaging;

import com.pte.common.messaging.AbstractOutboxRelay;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.OutboxEntry;
import com.pte.examdelivery.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Polls exam-delivery's outbox and publishes to {@link ExamDeliveryConstants#OUTBOX_EXCHANGE}
 * (ADR-002 supersession). Deployment constraint: run as a SINGLE instance —
 * scoring's {@code AnswerIngestConsumer} (Phase 6) depends on {@code AnswerSubmitted}
 * events arriving in the order this outbox wrote them (plan.md, CONFIRMED).
 */
@Component
public class ExamDeliveryOutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    public ExamDeliveryOutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        super(repository, rabbitTemplate, transactionManager);
    }

    @Override
    protected void publish(OutboxEntry entry) {
        String routingKey = entry.getAggregateType() + "." + entry.getEventType();
        publishAndConfirm(entry, ExamDeliveryConstants.OUTBOX_EXCHANGE, routingKey);
    }
}
