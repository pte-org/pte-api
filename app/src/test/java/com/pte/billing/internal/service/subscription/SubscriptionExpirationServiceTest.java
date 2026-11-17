package com.pte.billing.internal.service.subscription;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private Subscription dueSubscription() {
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExpiresAt(NOW.minusSeconds(1));
        return subscription;
    }

    private SubscriptionExpirationService service() {
        return new SubscriptionExpirationService(subscriptionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void expiresEveryDueSubscription() {
        Subscription first = dueSubscription();
        Subscription second = dueSubscription();
        when(subscriptionRepository.findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, NOW))
                .thenReturn(List.of(first, second));
        when(subscriptionRepository.findByPublicId(first.getPublicId())).thenReturn(Optional.of(first));
        when(subscriptionRepository.findByPublicId(second.getPublicId())).thenReturn(Optional.of(second));

        assertThat(service().expireDueSubscriptions()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void oneSubscriptionFailingDoesNotKeepTheOthersActive() {
        Subscription broken = dueSubscription();
        Subscription healthy = dueSubscription();
        when(subscriptionRepository.findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, NOW))
                .thenReturn(List.of(broken, healthy));
        when(subscriptionRepository.findByPublicId(broken.getPublicId()))
                .thenThrow(new IllegalStateException("cannot load"));
        when(subscriptionRepository.findByPublicId(healthy.getPublicId())).thenReturn(Optional.of(healthy));

        assertThat(service().expireDueSubscriptions()).isEqualTo(1);
        assertThat(healthy.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(broken.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void skipsASubscriptionThatIsNoLongerDueWhenItIsReloaded() {
        Subscription renewed = dueSubscription();
        when(subscriptionRepository.findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, NOW))
                .thenReturn(List.of(renewed));
        Subscription reloaded = dueSubscription();
        reloaded.setPublicId(renewed.getPublicId());
        reloaded.setExpiresAt(NOW.plusSeconds(3600));
        when(subscriptionRepository.findByPublicId(renewed.getPublicId())).thenReturn(Optional.of(reloaded));

        assertThat(service().expireDueSubscriptions()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
