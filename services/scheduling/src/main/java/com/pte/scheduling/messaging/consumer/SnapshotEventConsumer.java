package com.pte.scheduling.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.ProcessedEvent;
import com.pte.scheduling.domain.event.ExamSnapshotPublishedEvent;
import com.pte.scheduling.repository.ProcessedEventRepository;
import com.pte.scheduling.service.SnapshotItemSpec;
import com.pte.scheduling.service.SnapshotRefService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Realizes the Phase-4 "may instead consume ExamSnapshotPublished, removing
 * even the create-time pull" plan: proactively upserts {@code SnapshotRef} as
 * soon as authoring publishes, so most session-creation calls hit the cache
 * and never reach {@code AuthoringClient}. Idempotent (ADR-002) — dedups by
 * the producer's outbox row id (Debezium's {@code id} header) via
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

    @KafkaListener(topics = SchedulingConstants.TOPIC_SNAPSHOT_EVENTS, groupId = "scheduling-snapshot-ref")
    @Transactional
    public void onSnapshotEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = UUID.fromString(headerValue(record, "id"));
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(record, SchedulingConstants.KAFKA_HEADER_EVENT_TYPE);
        if (SchedulingConstants.INCOMING_EVENT_SNAPSHOT_PUBLISHED.equals(eventType)) {
            applyPublished(record.value());
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

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            throw new IllegalStateException("Missing Kafka header '" + key + "' on snapshot event");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
