/**
 * Exposed as a Spring Modulith named interface — {@code session} consumes
 * {@link com.pte.assessment.dto.response.SnapshotResponse} through {@code
 * AssessmentService#getSummary}. Default module visibility only exposes the
 * module's root package; this subpackage needs the explicit opt-in to be a
 * legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.assessment.dto.response;
