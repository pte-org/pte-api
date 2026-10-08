package com.pte.attempt.internal.policy;

/**
 * Sections whose tasks a student may move between with prev/next. Shared by
 * the server-side navigate check and the {@code canNavigate*} flags sent to
 * the client so the two can never disagree.
 */
public final class ManualNavigationPolicy {

    private ManualNavigationPolicy() {
    }

    public static boolean allowsSection(String section) {
        return "READING".equals(section) || "WRITING".equals(section);
    }
}
