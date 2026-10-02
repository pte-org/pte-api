package com.pte.itembank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict major.minor.patch comparison for release compatibility checks. */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    public static SemanticVersion parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("UNSUPPORTED_RUNTIME_CONTRACT");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("UNSUPPORTED_RUNTIME_CONTRACT");
        }
        return new SemanticVersion(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    public boolean satisfiesMinimum(String minimum) {
        return compareTo(parse(minimum)) >= 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) return majorComparison;
        int minorComparison = Integer.compare(minor, other.minor);
        return minorComparison != 0 ? minorComparison : Integer.compare(patch, other.patch);
    }
}
