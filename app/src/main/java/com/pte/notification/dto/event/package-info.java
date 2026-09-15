/**
 * Exposed as a Spring Modulith named interface — {@code reporting} (Phase 10,
 * not yet ported) will publish {@link com.pte.notification.dto.event.AttemptPublishedEvent}
 * once its own host-gated publish command exists. Default module visibility
 * only exposes the module's root package; this subpackage needs the explicit
 * opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.notification.dto.event;
