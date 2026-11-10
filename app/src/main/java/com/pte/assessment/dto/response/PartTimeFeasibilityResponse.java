package com.pte.assessment.dto.response;

/**
 * Planned duration of one Pearson part, in seconds, with the template's window for it. The frame values are null
 * when the template has no time frame; {@code canFitFrame} is then null too.
 */
public record PartTimeFeasibilityResponse(
        String part,
        long minSeconds,
        long maxSeconds,
        Integer frameMinSeconds,
        Integer frameMaxSeconds,
        Boolean canFitFrame) {
}
