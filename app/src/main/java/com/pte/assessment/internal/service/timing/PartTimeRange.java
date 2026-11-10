package com.pte.assessment.internal.service.timing;

/** Shortest and longest a part can take with the counts a template allows, in seconds. */
public record PartTimeRange(ExamTimePart part, long minSeconds, long maxSeconds) {
}
