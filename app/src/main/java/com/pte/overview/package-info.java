/**
 * Leaf module that composes dashboard aggregates from the public root facades of
 * other modules. No module depends on it, so it cannot create dependency cycles;
 * it never touches another module's repositories or entities.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Overview",
        allowedDependencies = {"shared", "tenancy", "identity", "enrollment", "session", "session::response",
                "attempt", "attempt::response", "attempt::enums", "scoring", "reporting", "billing", "support",
                "itembank"})
package com.pte.overview;
