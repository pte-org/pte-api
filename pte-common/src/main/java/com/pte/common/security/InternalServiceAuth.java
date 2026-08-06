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

    /**
     * Second, separate credential (rabbitmq-outbox-migration Phase 9) authorizing
     * the "export ALL tenants" mode on a {@code /internal/**\/export} endpoint —
     * used only by reporting's brand-new-instance full-bootstrap rebuild, never
     * by the normal per-tenant-JWT-forwarding internal calls. Checked by {@link
     * InternalBootstrapKeyFilter}, which runs AFTER {@link InternalApiKeyFilter}
     * and requires the base {@link #HEADER} key to ALSO be valid — this key
     * alone is never sufficient (defense in depth).
     */
    public static final String BOOTSTRAP_HEADER = "X-Internal-Bootstrap-Key";
    public static final String ROLE_INTERNAL_SERVICE_BOOTSTRAP = "INTERNAL_SERVICE_BOOTSTRAP";
    public static final String AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP = "ROLE_" + ROLE_INTERNAL_SERVICE_BOOTSTRAP;

    private InternalServiceAuth() {
    }
}
