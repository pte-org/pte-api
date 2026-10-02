package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SessionSubscriptionCapacityException extends DomainException {

    public SessionSubscriptionCapacityException(int requestedCapacity, int subscriptionCapacity) {
        this(SessionConstants.SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION,
                SessionConstants.SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_FRIENDLY,
                String.format(SessionConstants.SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_DETAIL,
                        requestedCapacity, subscriptionCapacity));
    }

    public static SessionSubscriptionCapacityException forEnrollments(long enrollmentCount,
                                                                        int subscriptionCapacity) {
        return new SessionSubscriptionCapacityException(
                SessionConstants.SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION,
                SessionConstants.SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_FRIENDLY,
                String.format(SessionConstants.SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_DETAIL,
                        enrollmentCount, subscriptionCapacity));
    }

    private SessionSubscriptionCapacityException(String code, String friendlyMessage, String diagnosticMessage) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, null, friendlyMessage, code + ": " + diagnosticMessage);
    }
}
