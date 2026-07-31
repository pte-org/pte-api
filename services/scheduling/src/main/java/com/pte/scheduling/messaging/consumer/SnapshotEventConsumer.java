package com.pte.scheduling.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.ProcessedEvent;
import com.pte.scheduling.domain.event.ExamSnapshotPublishedEvent;
import com.pte.scheduling.repository.ProcessedEventRepository;
import com.pte.scheduling.service.SnapshotItemSpec;
import com.pte.scheduling.service.SnapshotRefService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Realizes the Phase-4 (original walking-skeleton) "consume ExamSnapshotPublished,
 * removing even the create-time pull" plan: proactively upserts {@code SnapshotRef}
 * as soon as authoring publishes, so most session-creation calls hit the cache
 * and never reach {@code AuthoringClient}. Idempotent (ADR-002) — dedups by the
 * producer's outbox row id (delivered as the AMQP {@code messageId}, via the
 * polling outbox relay + RabbitMQ, superseding Debezium's outbox router) via
 * {@link ProcessedEvent} before applying.
 */
@Component
public class SnapshotEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final SnapshotRefService snapshotRefService;
    private final ObjectMapper objectMapper;

    public SnapshotEventConsumer(ProcessedEventRepository processedEventRepository,
                                 SnapshotRefService snapshotRefService, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.snapshotRefService = snapshotRefService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = SchedulingConstants.QUEUE_SNAPSHOT_EVENTS)
    @Transactional
    public void onSnapshotEvent(Message message) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, SchedulingConstants.EVENT_TYPE_HEADER);
        if (SchedulingConstants.INCOMING_EVENT_SNAPSHOT_PUBLISHED.equals(eventType)) {
            applyPublished(new String(message.getBody(), StandardCharsets.UTF_8));
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyPublished(String payload) throws IOException {
        ExamSnapshotPublishedEvent event = objectMapper.readValue(payload, ExamSnapshotPublishedEvent.class);
        var items = event.items().stream()
                .map(item -> new SnapshotItemSpec(item.orderIndex(), item.taskType(), item.section()))
                .toList();
        snapshotRefService.upsert(event.snapshotPublicId(), event.version(), event.name(), event.tenantId(), items);
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on snapshot event");
        }
        return value.toString();
    }
}
