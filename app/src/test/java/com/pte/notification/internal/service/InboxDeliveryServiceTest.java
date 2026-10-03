package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.config.InboxDeliveryProperties;
import com.pte.notification.internal.repository.InboxDeliveryClaim;
import com.pte.notification.internal.repository.InboxDeliveryRecord;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    @Mock private InboxDeliveryStore store;
    @Mock private InboxRecipientEligibility eligibility;
    private InboxDeliveryProperties properties;
    private InboxDeliveryService service;
    private InboxDeliveryClaim claim;
    private InboxDeliveryRecord record;

    @BeforeEach
    void setUp() {
        properties = new InboxDeliveryProperties();
        service = new InboxDeliveryService(store, eligibility, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        claim = new InboxDeliveryClaim(UUID.randomUUID(), UUID.randomUUID(), 1);
        record = new InboxDeliveryRecord(claim.publicId(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), InboxNotificationType.SESSION_CLOSING_SOON);
    }

    @Test
    void propertiesHaveBoundedDefaults() {
        assertThat(properties.getBatchSize()).isEqualTo(50);
        assertThat(properties.getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getLeaseSeconds()).isEqualTo(30);
        assertThat(properties.getInitialRetrySeconds()).isEqualTo(5);
        assertThat(properties.getMaxRetrySeconds()).isEqualTo(300);
    }

    @Test
    void claimExhaustsExpiredWorkBeforeBoundedLeaseClaim() {
        when(store.claimBatch(NOW, NOW.plusSeconds(30), 50, 5)).thenReturn(List.of(claim));
        assertThat(service.claimBatch()).containsExactly(claim);
        InOrder ordered = inOrder(store);
        ordered.verify(store).exhaustExpired(NOW, 5);
        ordered.verify(store).claimBatch(NOW, NOW.plusSeconds(30), 50, 5);
    }

    @Test
    void claimUsesConfiguredBoundsRatherThanStaticValues() {
        properties.setBatchSize(7);
        properties.setMaxAttempts(3);
        properties.setLeaseSeconds(11);
        when(store.claimBatch(NOW, NOW.plusSeconds(11), 7, 3)).thenReturn(List.of());
        assertThat(service.claimBatch()).isEmpty();
        verify(store).exhaustExpired(NOW, 3);
        verify(store).claimBatch(NOW, NOW.plusSeconds(11), 7, 3);
    }

    @Test
    void staleLookupDoesNotLockOrWrite() {
        when(store.findClaim(claim, NOW)).thenReturn(Optional.empty());
        assertThat(service.deliver(claim)).isFalse();
        verifyNoInteractions(eligibility);
        verify(store, never()).lockClaim(any(), any());
        assertNoTerminalWrite();
    }

    @Test
    void staleFenceAfterEligibilityNeverWrites() {
        when(store.findClaim(claim, NOW)).thenReturn(Optional.of(record));
        when(eligibility.lockEligible(record)).thenReturn(true);
        when(store.lockClaim(claim, NOW)).thenReturn(false);
        assertThat(service.deliver(claim)).isFalse();
        InOrder ordered = inOrder(eligibility, store);
        ordered.verify(eligibility).lockEligible(record);
        ordered.verify(store).lockClaim(claim, NOW);
        assertNoTerminalWrite();
    }

    @Test
    void eligibleFencedClaimDeliversAfterEligibilityGuards() {
        when(store.findClaim(claim, NOW)).thenReturn(Optional.of(record));
        when(eligibility.lockEligible(record)).thenReturn(true);
        when(store.lockClaim(claim, NOW)).thenReturn(true);
        assertThat(service.deliver(claim)).isTrue();
        InOrder ordered = inOrder(eligibility, store);
        ordered.verify(eligibility).lockEligible(record);
        ordered.verify(store).lockClaim(claim, NOW);
        ordered.verify(store).deliver(record, claim, NOW);
        verify(store, never()).suppress(any(), any());
    }

    @Test
    void ineligibleFencedClaimIsSuppressedNotDelivered() {
        when(store.findClaim(claim, NOW)).thenReturn(Optional.of(record));
        when(eligibility.lockEligible(record)).thenReturn(false);
        when(store.lockClaim(claim, NOW)).thenReturn(true);
        assertThat(service.deliver(claim)).isTrue();
        InOrder ordered = inOrder(eligibility, store);
        ordered.verify(eligibility).lockEligible(record);
        ordered.verify(store).lockClaim(claim, NOW);
        ordered.verify(store).suppress(claim, NOW);
        verify(store, never()).deliver(any(), any(), any());
    }

    @Test
    void ineligibleButStaleFenceDoesNotSuppress() {
        when(store.findClaim(claim, NOW)).thenReturn(Optional.of(record));
        when(eligibility.lockEligible(record)).thenReturn(false);
        when(store.lockClaim(claim, NOW)).thenReturn(false);
        assertThat(service.deliver(claim)).isFalse();
        assertNoTerminalWrite();
    }

    @Test
    void transientFailureRetriesAtFixedClockWithSanitizedConstantCode() {
        service.fail(claim, new TransientDataAccessResourceException("secret SQL body and password"));
        verify(store).fail(claim, NOW, NOW.plusSeconds(5), InboxDeliveryStatus.PENDING,
                "INBOX_DELIVERY_TRANSIENT_FAILURE");
    }

    @Test
    void retryBackoffIsExponentialAndCapped() {
        properties.setInitialRetrySeconds(10);
        properties.setMaxRetrySeconds(12);
        InboxDeliveryClaim second = new InboxDeliveryClaim(claim.publicId(), claim.token(), 2);
        service.fail(second, new TransientDataAccessResourceException("private database details"));
        verify(store).fail(second, NOW, NOW.plusSeconds(12), InboxDeliveryStatus.PENDING,
                "INBOX_DELIVERY_TRANSIENT_FAILURE");
    }

    @Test
    void exhaustedTransientFailureIsTerminalWithoutAnotherRetry() {
        InboxDeliveryClaim exhausted = new InboxDeliveryClaim(claim.publicId(), claim.token(), 5);
        service.fail(exhausted, new TransientDataAccessResourceException("private SQL"));
        verify(store).fail(eq(exhausted), eq(NOW), any(), eq(InboxDeliveryStatus.FAILED),
                eq("INBOX_DELIVERY_TRANSIENT_FAILURE"));
    }

    @Test
    void nonTransientFailureIsTerminalAndNeverPersistsExceptionMessage() {
        service.fail(claim, new IllegalArgumentException("secret body signed media url password"));
        verify(store).fail(eq(claim), eq(NOW), any(), eq(InboxDeliveryStatus.FAILED),
                eq("INBOX_DELIVERY_NON_RETRYABLE_FAILURE"));
    }

    @Test
    void manualRetryUsesOriginalIdentityAndFixedClock() {
        when(store.retryFailed(claim.publicId(), NOW)).thenReturn(true);
        assertThat(service.retryFailed(claim.publicId())).isTrue();
        verify(store).retryFailed(claim.publicId(), NOW);
    }

    @Test
    void retryMissingOrNonFailedIdentityDoesNotReportSuccess() {
        when(store.retryFailed(claim.publicId(), NOW)).thenReturn(false);
        assertThat(service.retryFailed(claim.publicId())).isFalse();
    }

    @Test
    void operationalMetadataRecordsCannotCarryBodyTitleOrExceptionDetails() {
        assertThat(Arrays.stream(InboxDeliveryRecord.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("deliveryPublicId", "contentPublicId", "recipientPublicId", "tenantId", "type");
        assertThat(record.toString()).doesNotContain("Body", "password", "Exception");
        assertThat(Arrays.stream(InboxDeliveryClaim.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("publicId", "token", "attempts");
    }

    private void assertNoTerminalWrite() {
        verify(store, never()).deliver(any(), any(), any());
        verify(store, never()).suppress(any(), any());
        verify(store, never()).fail(any(), any(), any(), any(), any());
    }
}
