package com.pte.iam.messaging.consumer;

import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.ProcessedEvent;
import com.pte.iam.domain.TenantRegistry;
import com.pte.iam.domain.enums.TenantRegistryStatus;
import com.pte.iam.domain.event.TenantOnboardedEvent;
import com.pte.iam.domain.event.TenantSuspendedEvent;
import com.pte.iam.repository.ProcessedEventRepository;
import com.pte.iam.repository.TenantRegistryRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Closes the ADR-001 "tenant registry lives in iam" gap: consumes admin's
 * {@code TenantOnboarded}/{@code TenantSuspended} via the polling outbox
 * relay + RabbitMQ (rabbitmq-outbox-migration Phase 3, superseding Debezium's
 * outbox router) and projects them into iam's own {@link TenantRegistry}.
 * Idempotent by design (ADR-002 — RabbitMQ is at-least-once): every event is
 * checked against {@link ProcessedEvent} BEFORE being applied, using the
 * producer's outbox row id (delivered as the AMQP {@code messageId}), never
 * trusting delivery count alone.
 */
@Component
public class TenantEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final JsonMapper jsonMapper;

    public TenantEventConsumer(ProcessedEventRepository processedEventRepository,
                               TenantRegistryRepository tenantRegistryRepository, JsonMapper jsonMapper) {
        this.processedEventRepository = processedEventRepository;
        this.tenantRegistryRepository = tenantRegistryRepository;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = IamConstants.QUEUE_TENANT_EVENTS)
    @Transactional
    public void onTenantEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        String eventType = headerValue(properties, IamConstants.EVENT_TYPE_HEADER);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (IamConstants.INCOMING_EVENT_TENANT_ONBOARDED.equals(eventType)) {
            applyOnboarded(payload);
        } else if (IamConstants.INCOMING_EVENT_TENANT_SUSPENDED.equals(eventType)) {
            applySuspended(payload);
        }
        // Unrecognized event type on this queue: ignore, don't fail the consumer —
        // forward-compatible with future event types this consumer doesn't need yet.

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private void applyOnboarded(String payload) {
        TenantOnboardedEvent event = jsonMapper.readValue(payload, TenantOnboardedEvent.class);
        TenantRegistry registry = tenantRegistryRepository.findByTenantPublicId(event.tenantPublicId())
                .orElseGet(TenantRegistry::new);
        registry.setTenantPublicId(event.tenantPublicId());
        registry.setStatus(TenantRegistryStatus.ACTIVE);
        tenantRegistryRepository.save(registry);
    }

    private void applySuspended(String payload) {
        TenantSuspendedEvent event = jsonMapper.readValue(payload, TenantSuspendedEvent.class);
        tenantRegistryRepository.findByTenantPublicId(event.tenantPublicId())
                .ifPresent(registry -> {
                    registry.setStatus(TenantRegistryStatus.SUSPENDED);
                    tenantRegistryRepository.save(registry);
                });
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on tenant event");
        }
        return value.toString();
    }
}
