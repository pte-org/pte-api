package com.pte.iam.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.ProcessedEvent;
import com.pte.iam.domain.TenantRegistry;
import com.pte.iam.domain.enums.TenantRegistryStatus;
import com.pte.iam.domain.event.TenantOnboardedEvent;
import com.pte.iam.domain.event.TenantSuspendedEvent;
import com.pte.iam.repository.ProcessedEventRepository;
import com.pte.iam.repository.TenantRegistryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Closes the ADR-001 "tenant registry lives in iam" gap (Phase 6): consumes
 * admin's {@code TenantOnboarded}/{@code TenantSuspended} via Debezium's outbox
 * router and projects them into iam's own {@link TenantRegistry}. Idempotent
 * by design (ADR-002 — Kafka is at-least-once): every event is checked against
 * {@link ProcessedEvent} BEFORE being applied, using the producer's outbox row
 * id (Debezium's {@code id} header), never trusting delivery count alone.
 */
@Component
public class TenantEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final ObjectMapper objectMapper;

    public TenantEventConsumer(ProcessedEventRepository processedEventRepository,
                               TenantRegistryRepository tenantRegistryRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.tenantRegistryRepository = tenantRegistryRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = IamConstants.TOPIC_TENANT_EVENTS, groupId = "iam-tenant-registry")
    @Transactional
    public void onTenantEvent(ConsumerRecord<String, String> record) throws IOException {
        UUID eventId = UUID.fromString(headerValue(record, "id"));
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(record, IamConstants.KAFKA_HEADER_EVENT_TYPE);
        if (IamConstants.INCOMING_EVENT_TENANT_ONBOARDED.equals(eventType)) {
            applyOnboarded(record.value());
        } else if (IamConstants.INCOMING_EVENT_TENANT_SUSPENDED.equals(eventType)) {
            applySuspended(record.value());
        }
        // Unrecognized event type on this topic: ignore, don't fail the consumer —
        // forward-compatible with future event types this consumer doesn't need yet.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyOnboarded(String payload) throws IOException {
        TenantOnboardedEvent event = objectMapper.readValue(payload, TenantOnboardedEvent.class);
        TenantRegistry registry = tenantRegistryRepository.findByTenantPublicId(event.tenantPublicId())
                .orElseGet(TenantRegistry::new);
        registry.setTenantPublicId(event.tenantPublicId());
        registry.setStatus(TenantRegistryStatus.ACTIVE);
        tenantRegistryRepository.save(registry);
    }

    private void applySuspended(String payload) throws IOException {
        TenantSuspendedEvent event = objectMapper.readValue(payload, TenantSuspendedEvent.class);
        tenantRegistryRepository.findByTenantPublicId(event.tenantPublicId())
                .ifPresent(registry -> {
                    registry.setStatus(TenantRegistryStatus.SUSPENDED);
                    tenantRegistryRepository.save(registry);
                });
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            throw new IllegalStateException("Missing Kafka header '" + key + "' on tenant event");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
