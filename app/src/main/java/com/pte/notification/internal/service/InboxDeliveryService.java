package com.pte.notification.internal.service;

import com.pte.notification.domain.enums.InboxDeliveryStatus;
import com.pte.notification.internal.config.InboxDeliveryProperties;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.repository.InboxDeliveryClaim;
import com.pte.notification.internal.repository.InboxDeliveryRecord;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional(timeout = 30)
public class InboxDeliveryService {
    private final InboxDeliveryStore store;
    private final InboxRecipientEligibility eligibility;
    private final InboxDeliveryProperties properties;
    private final Clock clock;
    public InboxDeliveryService(InboxDeliveryStore store, InboxRecipientEligibility eligibility,
            InboxDeliveryProperties properties, Clock clock) {
        this.store = store; this.eligibility = eligibility; this.properties = properties; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public List<InboxDeliveryClaim> claimBatch() {
        Instant now = clock.instant();
        store.exhaustExpired(now, properties.getMaxAttempts());
        return store.claimBatch(now, now.plusSeconds(properties.getLeaseSeconds()), properties.getBatchSize(), properties.getMaxAttempts());
    }

    public boolean deliver(InboxDeliveryClaim claim) {
        Optional<InboxDeliveryRecord> found = store.findClaim(claim, clock.instant());
        if (found.isEmpty()) { return false; }
        InboxDeliveryRecord record = found.get();
        boolean eligible = eligibility.lockEligible(record);
        Instant now = clock.instant();
        // Recheck ownership AFTER waiting for eligibility guards; an expired holder cannot write.
        if (!store.lockClaim(claim, now)) { return false; }
        if (eligible) { store.deliver(record, claim, now); }
        else { store.suppress(claim, now); }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void fail(InboxDeliveryClaim claim, RuntimeException failure) {
        Instant now = clock.instant();
        boolean transientFailure = failure instanceof TransientDataAccessException || failure instanceof DataAccessResourceFailureException;
        boolean retry = transientFailure && claim.attempts() < properties.getMaxAttempts();
        long jitter = ThreadLocalRandom.current().nextLong(properties.getRetryJitterSeconds() + 1);
        long delay = Math.min(properties.getMaxRetrySeconds(),
                properties.getInitialRetrySeconds() * (1L << Math.min(20, Math.max(0, claim.attempts() - 1))) + jitter);
        store.fail(claim, now, retry ? now.plusSeconds(delay) : now,
                retry ? InboxDeliveryStatus.PENDING : InboxDeliveryStatus.FAILED,
                transientFailure ? InboxConstants.TRANSIENT_FAILURE : InboxConstants.NON_RETRYABLE_FAILURE);
    }

    public boolean retryFailed(UUID publicId) { return store.retryFailed(publicId, clock.instant()); }
    @Transactional(readOnly = true)
    public long countByStatus(InboxDeliveryStatus status) { return store.countByStatus(status); }
}
