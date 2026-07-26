package com.pte.proctor.security;

import com.pte.common.security.CurrentUser;

import java.security.Principal;

/** Wraps the {@link CurrentUser} resolved at STOMP CONNECT so later frames on the same session can read it back. */
public record StompPrincipal(CurrentUser currentUser) implements Principal {

    @Override
    public String getName() {
        return currentUser.userId().toString();
    }
}
