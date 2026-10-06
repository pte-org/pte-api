package com.pte.billing.internal.service;

import com.pte.billing.domain.Subscription;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionPersistenceServiceTest {

    @Test
    void saveJoinsActivationTransactionSoRollbackCannotLeaveOrphanSubscription() throws Exception {
        Transactional transactional = SubscriptionPersistenceService.class
                .getDeclaredMethod("save", Subscription.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
