package com.aptis.modules.proctor.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.aptis.common.event.UserLoggedOutEvent;
import com.aptis.modules.iam.constant.IamApiConstants;

/**
 * Closes the loop on Design Constraint / Step 8: a proctor's stale WebSocket connection
 * must not keep receiving messages after HTTP logout. Listens for the neutral
 * {@link UserLoggedOutEvent} (published by iam's AuthService) rather than iam depending on
 * this module directly, avoiding an iam↔proctor cycle.
 */
@Component
public class ProctorLogoutListener {

    private final ProctorWebSocketSessionRegistry sessionRegistry;

    public ProctorLogoutListener(ProctorWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @EventListener
    public void onUserLoggedOut(UserLoggedOutEvent event) {
        if (IamApiConstants.USER_TYPE_PROCTOR.equals(event.userType())) {
            sessionRegistry.revokeAllForUser(event.userId());
        }
    }
}
