package com.pte.identity;

import java.util.List;
import java.util.UUID;

/**
 * Asks notification to e-mail reset links. One mail carries a link per account sharing the address; each link is a
 * secret and must not be logged or stored in notification history.
 */
public record PasswordResetEmailRequestedEvent(UUID requestId, String email, List<Link> links) {

    public record Link(String username, String url) {
    }
}
