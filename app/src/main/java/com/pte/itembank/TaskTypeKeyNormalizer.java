package com.pte.itembank;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical normalization rules for the logical task-type identity.
 *
 * <p>The key is an API/domain identity, not a display label. Keeping this
 * implementation in the public itembank contract prevents each caller from
 * inventing a slightly different uniqueness rule.</p>
 */
public final class TaskTypeKeyNormalizer {

    public static final int MIN_KEY_LENGTH = 2;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MIN_DISPLAY_NAME_LENGTH = 1;
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private TaskTypeKeyNormalizer() {
    }

    /** Normalize and validate a task key according to the platform contract. */
    public static String normalizeKey(String raw) {
        Objects.requireNonNull(raw, "taskTypeKey");
        String normalized = stripSurroundingUnicodeWhitespace(
                Normalizer.normalize(raw, Normalizer.Form.NFKC))
                .toUpperCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("TASK_TYPE_KEY_INVALID");
        }
        return normalized;
    }

    /** Normalize only the value used for display-name uniqueness comparison. */
    public static String normalizeDisplayName(String raw) {
        Objects.requireNonNull(raw, "displayName");
        String nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder collapsed = new StringBuilder(nfkc.length());
        boolean whitespace = false;
        for (int offset = 0; offset < nfkc.length();) {
            int codePoint = nfkc.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isUnicodeWhitespace(codePoint)) {
                whitespace = true;
                continue;
            }
            if (whitespace && !collapsed.isEmpty()) {
                collapsed.append(' ');
            }
            whitespace = false;
            collapsed.appendCodePoint(codePoint);
        }
        String normalized = collapsed.toString().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_DISPLAY_NAME_LENGTH
                || normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("TASK_TYPE_DISPLAY_NAME_INVALID");
        }
        return normalized;
    }

    /** Preserve a readable label while applying the same Unicode normalization. */
    public static String normalizeDisplayLabel(String raw) {
        Objects.requireNonNull(raw, "displayName");
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "")
                .trim();
        if (normalized.isEmpty() || normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("TASK_TYPE_DISPLAY_NAME_INVALID");
        }
        return normalized;
    }

    public static boolean isValidKey(String raw) {
        try {
            normalizeKey(raw);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String stripSurroundingUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
