/**
 * Exposed as a Spring Modulith named interface — {@code reporting} (Phase 10)
 * consumes {@link com.pte.scoring.dto.response.ScoredAnswerView} through
 * {@code ScoringService#getScoredAnswersForAttempt}. Default module
 * visibility only exposes the module's root package; this subpackage needs
 * the explicit opt-in to be a legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.scoring.dto.response;
