/**
 * Exposed as a Spring Modulith named interface — {@code notification} (Phase 09)
 * consumes {@link com.pte.identity.domain.User}/{@link com.pte.identity.domain.Role}
 * through {@code IdentityService#findById}/{@code #findByTenantIdAndRole}.
 * Default module visibility only exposes the module's root package; this
 * subpackage needs the explicit opt-in to be a legal cross-module dependency
 * target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.identity.domain;
