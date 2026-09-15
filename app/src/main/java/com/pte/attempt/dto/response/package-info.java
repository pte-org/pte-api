/**
 * Exposed as a Spring Modulith named interface — {@code scoring} (Phase 08)
 * consumes {@link com.pte.attempt.dto.response.SubmittedAnswerView} through
 * {@code AttemptService#getSubmittedAnswersForSession}. Default module
 * visibility only exposes the module's root package; this subpackage needs
 * the explicit opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.attempt.dto.response;
