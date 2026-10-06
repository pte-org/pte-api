package com.pte.billing.internal.service;

import com.pte.billing.domain.Subscription;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps subscription creation in the caller's transaction. The subscription,
 * access/quota transition and its notification intent must commit or roll back
 * together; a database uniqueness conflict is allowed to abort the whole
 * business transition so a caller can safely retry the original operation.
 */
@Service
public class SubscriptionPersistenceService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionPersistenceService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public Subscription save(Subscription subscription) {
        return subscriptionRepository.saveAndFlush(subscription);
    }
}
