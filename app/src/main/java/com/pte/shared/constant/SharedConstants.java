package com.pte.shared.constant;

/** Constants shared by cross-cutting infrastructure. */
public final class SharedConstants {

    public static final String RESOURCE_VERSION_CONFLICT = "RESOURCE_VERSION_CONFLICT";
    public static final String RESOURCE_VERSION_CONFLICT_MESSAGE = "This resource changed while you were editing. Reload before trying again.";

    public static final String VALIDATION_FALLBACK = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String MALFORMED_REQUEST_MESSAGE = "The request body is invalid or contains a value out of range.";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String AUDIT_AUTHORIZATION_DENIED = "AUTHORIZATION_DENIED";
    public static final String NO_AUTHENTICATED_PRINCIPAL = "No authenticated principal";
    public static final String NO_AUTHENTICATED_STOMP_PRINCIPAL = "No authenticated STOMP principal";

    private SharedConstants() {
    }
}
