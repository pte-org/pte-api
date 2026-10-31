package com.pte.notification.internal.template;

/** A mail ready to queue: the HTML card and the plain-text twin sent with it for clients that do not show HTML. */
public record RenderedEmail(String subject, String text, String html) {
}
