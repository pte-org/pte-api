package com.pte.billing.internal.service;

import com.pte.billing.domain.Subscription;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates each license-key insert in its own transaction so a rare unique-key
 * collision can be retried after PostgreSQL aborts the failed transaction.
 */
@Service
public class SubscriptionPersistenceService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionPersistenceService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription save(Subscription subscription) {
        return subscriptionRepository.saveAndFlush(subscription);
    }
}
