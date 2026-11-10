package com.pte.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** The share, in whole percent, of one CEFR level an exam built from a template should aim for. */
@Embeddable
public record ExamCefrTarget(
        @Column(name = "level", nullable = false, length = 2) String level,
        @Column(name = "percent", nullable = false) int percent) {
}
