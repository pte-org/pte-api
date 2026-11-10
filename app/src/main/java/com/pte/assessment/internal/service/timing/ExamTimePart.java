package com.pte.assessment.internal.service.timing;

import java.util.Locale;

/** The three parts Pearson times separately; Speaking and Writing share one part. */
public enum ExamTimePart {
    SPEAKING_WRITING,
    READING,
    LISTENING;

    public static ExamTimePart ofSection(String section) {
        return switch (section.trim().toUpperCase(Locale.ROOT)) {
            case "SPEAKING", "WRITING" -> SPEAKING_WRITING;
            case "READING" -> READING;
            case "LISTENING" -> LISTENING;
            default -> throw new IllegalArgumentException(section);
        };
    }
}
