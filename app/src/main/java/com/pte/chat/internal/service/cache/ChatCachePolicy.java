package com.pte.chat.internal.service.cache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a question may enter the answer cache shared by all students. A cached answer is replayed to
 * strangers and may repeat what the student typed, so a long question (likely a pasted essay) or one with an
 * email address or phone number is answered normally and never stored for others. This is a safety net for
 * the common cases, not a privacy filter: names and other free text are not detected.
 */
public final class ChatCachePolicy {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");
    /** A run of digits that may be split by spaces, dots, dashes or brackets, as phone numbers usually are. */
    private static final Pattern DIGIT_RUN = Pattern.compile("[+(]?\\d[\\d ().\\-]{6,}\\d");
    private static final int MIN_PHONE_DIGITS = 8;

    private ChatCachePolicy() {
    }

    public static boolean cacheable(String question, int maxChars) {
        if (question.length() > maxChars) {
            return false;
        }
        return !EMAIL.matcher(question).find() && !hasPhoneNumber(question);
    }

    private static boolean hasPhoneNumber(String question) {
        Matcher run = DIGIT_RUN.matcher(question);
        while (run.find()) {
            if (run.group().chars().filter(Character::isDigit).count() >= MIN_PHONE_DIGITS) {
                return true;
            }
        }
        return false;
    }
}
