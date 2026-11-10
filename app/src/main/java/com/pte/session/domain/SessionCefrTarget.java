package com.pte.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** The share, in whole percent, of one CEFR level an exam should aim for. */
@Embeddable
public record SessionCefrTarget(
        @Column(name = "level", nullable = false, length = 2) String level,
        @Column(name = "percent", nullable = false) int percent) {
}
