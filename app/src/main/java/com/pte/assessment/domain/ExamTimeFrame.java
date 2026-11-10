package com.pte.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The Pearson time window of a template, in seconds: the range each of the three parts of the test may take,
 * and a ceiling for the whole test. A template without a frame has no time limits of its own.
 */
@Embeddable
public record ExamTimeFrame(
        @Column(name = "sw_min_seconds") Integer speakingWritingMinSeconds,
        @Column(name = "sw_max_seconds") Integer speakingWritingMaxSeconds,
        @Column(name = "reading_min_seconds") Integer readingMinSeconds,
        @Column(name = "reading_max_seconds") Integer readingMaxSeconds,
        @Column(name = "listening_min_seconds") Integer listeningMinSeconds,
        @Column(name = "listening_max_seconds") Integer listeningMaxSeconds,
        @Column(name = "total_max_seconds") Integer totalMaxSeconds) {
}
