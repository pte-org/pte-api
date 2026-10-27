package com.pte.billing.internal.service.subscription;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Marks expired subscriptions; reads also check the timestamp defensively. */
@Service
public class SubscriptionExpirationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public SubscriptionExpirationService(SubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${billing.subscription-expiration.cron:0 * * * * *}")
    @Transactional
    public int expireDueSubscriptions() {
        Instant now = clock.instant();
        int expired = 0;
        for (Subscription subscription : subscriptionRepository
                .findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, now)) {
            subscription.expire();
            expired++;
        }
        if (expired > 0) {
            log.info("Expired {} billing subscriptions", expired);
        }
        return expired;
    }
}
