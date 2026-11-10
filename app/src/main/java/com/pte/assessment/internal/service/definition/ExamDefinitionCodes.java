package com.pte.assessment.internal.service.definition;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Derives the code of a new template family from its name, for callers that do not supply one. */
final class ExamDefinitionCodes {

    private static final int MAX_LENGTH = 64;
    private static final String FALLBACK = "template";

    private ExamDefinitionCodes() {
    }

    /**
     * A lower-case, hyphenated form of {@code name}, with a numeric suffix when {@code isTaken} says another
     * family already uses it, so a new template never lands in an unrelated family's version line.
     */
    static String fromName(String name, Predicate<String> isTaken) {
        String base = slug(name);
        String candidate = base;
        for (int suffix = 2; isTaken.test(candidate); suffix++) {
            String tail = "-" + suffix;
            candidate = base.substring(0, Math.min(base.length(), MAX_LENGTH - tail.length())) + tail;
        }
        return candidate;
    }

    private static String slug(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = FALLBACK;
        }
        return slug.length() > MAX_LENGTH ? slug.substring(0, MAX_LENGTH).replaceAll("-+$", "") : slug;
    }
}
