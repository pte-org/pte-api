package com.pte.itembank.domain.enums;

import java.util.Locale;

/**
 * Level of the Common European Framework of Reference for Languages a question is written for. The standard PTE
 * Academic exam draws only from {@link #B1} to {@link #C2}; the lower levels exist for centres that teach
 * learners below that range.
 */
public enum CefrLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2;

    /** The level named by {@code value}, or null when it is null or blank; throws for any other text. */
    public static CefrLevel parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
