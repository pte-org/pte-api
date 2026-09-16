/**
 * Exposed as a Spring Modulith named interface — {@code notification}
 * (Phase 09) listens for {@link com.pte.proctoring.dto.event.ViolationDetectedEvent}
 * via {@code @TransactionalEventListener}. Default module visibility only
 * exposes the module's root package; this subpackage needs the explicit
 * opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.proctoring.dto.event;
