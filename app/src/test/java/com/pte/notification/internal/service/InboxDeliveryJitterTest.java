package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.notification.internal.config.InboxDeliveryProperties;
import com.pte.notification.internal.repository.InboxDeliveryClaim;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InboxDeliveryJitterTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void retryWithJitterRemainsWithinBaseAndBasePlusConfiguredWindow() {
        InboxDeliveryProperties properties = new InboxDeliveryProperties();
        properties.setRetryJitterSeconds(3);
        assertRetryRange(properties, 1, NOW.plusSeconds(5), NOW.plusSeconds(8));
    }

    @Test
    void retryJitterCannotExceedMaximumBackoff() {
        InboxDeliveryProperties properties = new InboxDeliveryProperties();
        properties.setInitialRetrySeconds(10);
        properties.setMaxRetrySeconds(12);
        properties.setRetryJitterSeconds(3);
        assertRetryRange(properties, 2, NOW.plusSeconds(12), NOW.plusSeconds(12));
    }

    private static void assertRetryRange(InboxDeliveryProperties properties, int attempts,
                                         Instant minimum, Instant maximum) {
        InboxDeliveryStore store = mock(InboxDeliveryStore.class);
        InboxDeliveryService service = new InboxDeliveryService(store, mock(InboxRecipientEligibility.class),
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
        InboxDeliveryClaim claim = new InboxDeliveryClaim(UUID.randomUUID(), UUID.randomUUID(), attempts);
        service.fail(claim, new TransientDataAccessResourceException("private database detail"));
        ArgumentCaptor<Instant> scheduled = ArgumentCaptor.forClass(Instant.class);
        verify(store).fail(eq(claim), eq(NOW), scheduled.capture(), eq(InboxDeliveryStatus.PENDING),
                eq("INBOX_DELIVERY_TRANSIENT_FAILURE"));
        assertThat(scheduled.getValue()).isBetween(minimum, maximum);
    }
}
