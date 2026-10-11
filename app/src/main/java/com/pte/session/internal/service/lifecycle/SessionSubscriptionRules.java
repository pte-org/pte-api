package com.pte.session.internal.service.lifecycle;

import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.internal.exception.InvalidSessionWindowException;
import com.pte.session.internal.exception.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Subscription entitlement, schedule overlap, and constraint translation shared by lifecycle commands. */
@Service
public class SessionSubscriptionRules {

    private static final String OVERLAP_CONSTRAINT = "no_overlap_per_subscription";

    private final ExamSessionRepository sessionRepository;
    private final BillingService billingService;

    public SessionSubscriptionRules(ExamSessionRepository sessionRepository,
            BillingService billingService) {
        this.sessionRepository = sessionRepository;
        this.billingService = billingService;
    }

    public SubscriptionView lockedActiveSubscription(UUID subscriptionId, UUID tenantId) {
        if (billingService == null || subscriptionId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<SubscriptionView> locked = billingService.lockSubscriptions(List.of(subscriptionId), tenantId);
        if (!locked.isEmpty()) {
            return locked.get(0);
        }
        return billingService.getActiveSubscription(subscriptionId, tenantId)
                .orElseThrow(SessionSubscriptionNotFoundException::new);
    }

    public void validateSubscriptionWindowAndCapacity(SubscriptionView subscription, Instant opensAt,
            Instant closesAt, long capacityOrEnrollmentCount, boolean enrollmentCount) {
        Instant now = Instant.now();
        if (subscription == null || subscription.status() == null || subscription.maxStudentsPerSession() == null
                || subscription.maxStudentsPerSession() <= 0 || !subscription.isUsableAt(now)) {
            throw new SessionSubscriptionNotFoundException();
        }
        if (opensAt.isBefore(subscription.startsAt()) || closesAt.isAfter(subscription.expiresAt())) {
            throw new SessionWindowOutsideSubscriptionException();
        }
        if (enrollmentCount) {
            if (capacityOrEnrollmentCount > subscription.maxStudentsPerSession()) {
                throw SessionSubscriptionCapacityException.forEnrollments(
                        capacityOrEnrollmentCount, subscription.maxStudentsPerSession());
            }
        } else if (capacityOrEnrollmentCount > subscription.maxStudentsPerSession()) {
            throw new SessionSubscriptionCapacityException((int) capacityOrEnrollmentCount,
                    subscription.maxStudentsPerSession());
        }
    }

    public void validateWindow(Instant opensAt, Instant closesAt) {
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new InvalidSessionWindowException();
        }
    }

    public void rejectOverlap(UUID subscriptionId, Instant opensAt, Instant closesAt, UUID excludedPublicId) {
        sessionRepository.findFirstOverlapping(subscriptionId, opensAt, closesAt, excludedPublicId)
                .ifPresent(conflict -> {
                    throw new SessionTimeConflictException(conflict.getPublicId());
                });
    }

    public RuntimeException translateOverlap(DataIntegrityViolationException ex) {
        if (containsConstraint(ex, OVERLAP_CONSTRAINT)) {
            return new SessionTimeConflictException();
        }
        return ex;
    }

    private boolean containsConstraint(Throwable throwable, String constraint) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
