package com.pte.admin.messaging.consumer;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.messaging.EventIdempotencyGuard;
import com.pte.admin.messaging.consumer.dto.UserCreatedEvent;
import com.pte.admin.messaging.consumer.dto.UserReactivatedEvent;
import com.pte.admin.messaging.consumer.dto.UserSuspendedEvent;
import com.pte.admin.repository.StudentRosterEntryRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Projects IAM user lifecycle events into Admin's rebuildable student roster.
 * The event ledger and projection write run in the same transaction so a
 * failed projection is retried rather than being marked as processed.
 */
@Component
public class StudentRosterConsumer {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String SUSPENDED_STATUS = "SUSPENDED";

    private final EventIdempotencyGuard idempotencyGuard;
    private final StudentRosterEntryRepository rosterRepository;
    private final JsonMapper jsonMapper;

    public StudentRosterConsumer(EventIdempotencyGuard idempotencyGuard,
                                 StudentRosterEntryRepository rosterRepository,
                                 JsonMapper jsonMapper) {
        this.idempotencyGuard = idempotencyGuard;
        this.rosterRepository = rosterRepository;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = AdminConstants.QUEUE_USER_EVENTS,
            containerFactory = "eventBackboneListenerContainerFactory")
    @Transactional
    public void onUserEvent(Message message) {
        MessageProperties properties = message.getMessageProperties();
        UUID eventId = UUID.fromString(properties.getMessageId());
        if (idempotencyGuard.alreadyProcessed(eventId)) {
            return;
        }

        String eventType = headerValue(properties, AdminConstants.EVENT_TYPE_HEADER);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (AdminConstants.INCOMING_EVENT_USER_CREATED.equals(eventType)) {
            applyCreated(payload);
        } else if (AdminConstants.INCOMING_EVENT_USER_SUSPENDED.equals(eventType)) {
            applySuspended(payload);
        } else if (AdminConstants.INCOMING_EVENT_USER_REACTIVATED.equals(eventType)) {
            applyReactivated(payload);
        }

        idempotencyGuard.markProcessed(eventId);
    }

    private void applyCreated(String payload) {
        UserCreatedEvent event = jsonMapper.readValue(payload, UserCreatedEvent.class);
        if (!hasStudentRole(event.roles())) {
            return;
        }
        // Events emitted by an older IAM build may not contain the additive
        // projection fields. Backfill will rebuild those users from IAM; do
        // not insert an incomplete row into the non-null projection schema.
        if (event.fullName() == null || event.status() == null || event.createdAt() == null) {
            return;
        }

        rosterRepository.upsert(event.userPublicId(), event.tenantId(), event.email(), event.fullName(),
                event.studentCode(), event.phone(), event.status(), event.createdAt());
    }

    private void applySuspended(String payload) {
        UserSuspendedEvent event = jsonMapper.readValue(payload, UserSuspendedEvent.class);
        applyStatus(event.tenantId(), event.userPublicId(), SUSPENDED_STATUS);
    }

    private void applyReactivated(String payload) {
        UserReactivatedEvent event = jsonMapper.readValue(payload, UserReactivatedEvent.class);
        applyStatus(event.tenantId(), event.userPublicId(), ACTIVE_STATUS);
    }

    private void applyStatus(UUID tenantId, UUID userPublicId, String status) {
        var entry = rosterRepository.findByTenantIdAndStudentPublicId(tenantId, userPublicId);
        entry
                .ifPresent(savedEntry -> {
                    savedEntry.setStatus(status);
                    rosterRepository.save(savedEntry);
                });
    }

    private boolean hasStudentRole(List<String> roles) {
        return roles != null && roles.contains(STUDENT_ROLE);
    }

    private String headerValue(MessageProperties properties, String key) {
        Object value = properties.getHeaders().get(key);
        if (value == null) {
            throw new IllegalStateException("Missing AMQP header '" + key + "' on user event");
        }
        return value.toString();
    }
}
