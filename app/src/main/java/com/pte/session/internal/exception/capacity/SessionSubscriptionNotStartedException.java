package com.pte.session.internal.exception.capacity;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** An exam on a granted lane that starts later can be scheduled, but not opened before the lane starts. */
public class SessionSubscriptionNotStartedException extends DomainException {

    public SessionSubscriptionNotStartedException(Instant startsAt) {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_SUBSCRIPTION_NOT_STARTED,
                Map.of("subscriptionStartsAt", startsAt),
                SessionConstants.SESSION_SUBSCRIPTION_NOT_STARTED_FRIENDLY.formatted(
                        SessionConstants.CONTRACT_DATE.format(startsAt)));
    }
}
