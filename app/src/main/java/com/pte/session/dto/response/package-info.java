/**
 * Exposed as a Spring Modulith named interface — {@code attempt} (Phase 07)
 * and {@code proctoring} (Phase 09) will consume {@link
 * com.pte.session.dto.response.EntitlementResponse} and {@link
 * com.pte.session.dto.response.ProctorAssignmentCheckResponse} through {@code
 * SessionService#checkEntitlement}/{@code #checkProctorAssignment}. Default
 * module visibility only exposes the module's root package; this subpackage
 * needs the explicit opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.session.dto.response;
