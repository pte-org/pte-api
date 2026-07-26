package com.pte.common.security;

/**
 * Contract for the internal service-to-service trust boundary. A placeholder
 * for the mTLS/service-mesh trust ADR-003 defers (Linkerd) — a shared key
 * checked by {@link InternalApiKeyFilter} on a service's {@code /internal/**}
 * paths, until real mTLS lands. NOT a substitute for per-caller authorization:
 * callers must still pass and each service must still validate any
 * caller-identity data (e.g. a student's own publicId) explicitly — the key
 * only proves "this call came from another trusted service," not "on behalf
 * of whom."
 */
public final class InternalServiceAuth {

    public static final String HEADER = "X-Internal-Service-Key";
    public static final String ROLE_INTERNAL_SERVICE = "INTERNAL_SERVICE";
    public static final String AUTHORITY_INTERNAL_SERVICE = "ROLE_" + ROLE_INTERNAL_SERVICE;
    public static final String INTERNAL_PATH_PREFIX = "/internal/**";

    private InternalServiceAuth() {
    }
}
