package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionSubscriptionCapacityException extends DomainException {

    public SessionSubscriptionCapacityException(int requestedCapacity, int subscriptionCapacity) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                SessionConstants.SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION + ": "
                        + String.format(SessionConstants.SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_DETAIL,
                        requestedCapacity, subscriptionCapacity));
    }

    public static SessionSubscriptionCapacityException forEnrollments(long enrollmentCount,
                                                                        int subscriptionCapacity) {
        return new SessionSubscriptionCapacityException(
                SessionConstants.SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION + ": "
                        + String.format(SessionConstants.SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_DETAIL,
                        enrollmentCount, subscriptionCapacity));
    }

    private SessionSubscriptionCapacityException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
