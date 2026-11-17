package com.pte.identity;

import java.util.List;
import java.util.UUID;

/**
 * Asks notification to e-mail reset links. Normally one mail carries one link, for the account the requester named;
 * a request without a username (old web bundles) gets a link per account sharing the address. Each link is a
 * secret and must not be logged or stored in notification history.
 */
public record PasswordResetEmailRequestedEvent(UUID requestId, String email, List<Link> links, int expiresInMinutes) {

    public record Link(String username, String url) {
    }
}
