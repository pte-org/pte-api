package com.pte.iam.messaging.consumer;

import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.ProcessedEvent;
import com.pte.iam.domain.TenantRegistry;
import com.pte.iam.domain.enums.TenantRegistryStatus;
import com.pte.iam.repository.ProcessedEventRepository;
import com.pte.iam.repository.TenantRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private TenantRegistryRepository tenantRegistryRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private TenantEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TenantEventConsumer(processedEventRepository, tenantRegistryRepository, jsonMapper);
    }

    private Message eventMessage(UUID eventId, String eventType, UUID tenantPublicId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(eventId.toString());
        properties.getHeaders().put(IamConstants.EVENT_TYPE_HEADER, eventType);
        String payload = "{\"tenantPublicId\":\"" + tenantPublicId + "\"}";
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void onboarded_createsActiveRegistryEntry() {
        UUID tenantPublicId = UUID.randomUUID();
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(tenantRegistryRepository.findByTenantPublicId(tenantPublicId)).thenReturn(Optional.empty());

        consumer.onTenantEvent(eventMessage(UUID.randomUUID(), IamConstants.INCOMING_EVENT_TENANT_ONBOARDED, tenantPublicId));

        ArgumentCaptor<TenantRegistry> captor = ArgumentCaptor.forClass(TenantRegistry.class);
        verify(tenantRegistryRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantRegistryStatus.ACTIVE);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void suspended_setsRegistryStatusSuspended() {
        UUID tenantPublicId = UUID.randomUUID();
        TenantRegistry registry = new TenantRegistry();
        registry.setTenantPublicId(tenantPublicId);
        registry.setStatus(TenantRegistryStatus.ACTIVE);
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(tenantRegistryRepository.findByTenantPublicId(tenantPublicId)).thenReturn(Optional.of(registry));

        consumer.onTenantEvent(eventMessage(UUID.randomUUID(), IamConstants.INCOMING_EVENT_TENANT_SUSPENDED, tenantPublicId));

        assertThat(registry.getStatus()).isEqualTo(TenantRegistryStatus.SUSPENDED);
        verify(tenantRegistryRepository).save(registry);
    }

    @Test
    void reactivated_setsRegistryStatusBackToActive() {
        UUID tenantPublicId = UUID.randomUUID();
        TenantRegistry registry = new TenantRegistry();
        registry.setTenantPublicId(tenantPublicId);
        registry.setStatus(TenantRegistryStatus.SUSPENDED);
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(tenantRegistryRepository.findByTenantPublicId(tenantPublicId)).thenReturn(Optional.of(registry));

        consumer.onTenantEvent(eventMessage(UUID.randomUUID(), IamConstants.INCOMING_EVENT_TENANT_REACTIVATED, tenantPublicId));

        assertThat(registry.getStatus()).isEqualTo(TenantRegistryStatus.ACTIVE);
        verify(tenantRegistryRepository).save(registry);
    }

    @Test
    void reactivated_unknownTenant_isNoOpNotAnError() {
        UUID tenantPublicId = UUID.randomUUID();
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(tenantRegistryRepository.findByTenantPublicId(tenantPublicId)).thenReturn(Optional.empty());

        consumer.onTenantEvent(eventMessage(UUID.randomUUID(), IamConstants.INCOMING_EVENT_TENANT_REACTIVATED, tenantPublicId));

        verify(tenantRegistryRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEventId_isSkippedBeforeApplying() {
        UUID eventId = UUID.randomUUID();
        UUID tenantPublicId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onTenantEvent(eventMessage(eventId, IamConstants.INCOMING_EVENT_TENANT_ONBOARDED, tenantPublicId));

        verify(tenantRegistryRepository, never()).findByTenantPublicId(any());
        verify(processedEventRepository, never()).save(any());
    }
}
