/**
 * Exposed as a Spring Modulith named interface — {@code assessment} (pin at
 * publish), {@code attempt} (timing), {@code scoring} (scoringMethod), and
 * {@code reporting} (weights) all consume
 * {@link com.pte.scoretemplate.dto.response.ScoreTemplateResponse} through
 * {@code ScoreTemplateService}. Default module visibility only exposes the
 * module's root package; this subpackage needs the explicit opt-in to be a
 * legal cross-module dependency target.
 */
@org.springframework.modulith.NamedInterface
package com.pte.scoretemplate.dto.response;
