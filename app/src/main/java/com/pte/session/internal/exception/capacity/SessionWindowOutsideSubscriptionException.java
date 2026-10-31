package com.pte.session.internal.exception.capacity;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public class SessionWindowOutsideSubscriptionException extends DomainException {

    public SessionWindowOutsideSubscriptionException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION,
                null, SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION_FRIENDLY);
    }

    /** Names the subscription period so the host can pick a fitting window or lane. */
    public SessionWindowOutsideSubscriptionException(Instant startsAt, Instant expiresAt) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION,
                Map.of("subscriptionStartsAt", startsAt, "subscriptionExpiresAt", expiresAt),
                SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION_RANGE_FRIENDLY.formatted(
                        SessionConstants.CONTRACT_DATE.format(startsAt),
                        SessionConstants.CONTRACT_DATE.format(expiresAt)));
    }
}
