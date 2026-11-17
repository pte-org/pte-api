package com.pte.billing.internal.service.subscription;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Marks expired subscriptions; reads also check the timestamp defensively. */
@Service
public class SubscriptionExpirationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;
    private final TransactionOperations transactions;

    public SubscriptionExpirationService(SubscriptionRepository subscriptionRepository, Clock clock) {
        this(subscriptionRepository, clock, TransactionOperations.withoutTransaction());
    }

    @Autowired
    public SubscriptionExpirationService(SubscriptionRepository subscriptionRepository, Clock clock,
            PlatformTransactionManager transactionManager) {
        this(subscriptionRepository, clock, new TransactionTemplate(transactionManager));
    }

    SubscriptionExpirationService(SubscriptionRepository subscriptionRepository, Clock clock,
            TransactionOperations transactions) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** One transaction per subscription: a row that cannot be updated must not keep the others active. */
    @Scheduled(cron = "${billing.subscription-expiration.cron:0 * * * * *}")
    public int expireDueSubscriptions() {
        Instant now = clock.instant();
        int expired = 0;
        for (Subscription due : subscriptionRepository
                .findByDeletedFalseAndStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, now)) {
            UUID subscriptionPublicId = due.getPublicId();
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> expireIfStillDue(subscriptionPublicId, now)))) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to expire subscription {}", subscriptionPublicId, exception);
            }
        }
        if (expired > 0) {
            log.info("Expired {} billing subscriptions", expired);
        }
        return expired;
    }

    private boolean expireIfStillDue(UUID subscriptionPublicId, Instant now) {
        Subscription subscription = subscriptionRepository.findByPublicId(subscriptionPublicId).orElse(null);
        if (subscription == null || subscription.isDeleted() || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.getExpiresAt() == null || subscription.getExpiresAt().isAfter(now)) {
            return false;
        }
        subscription.expire();
        return true;
    }
}
