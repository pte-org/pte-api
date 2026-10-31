package com.pte.chat.internal.service.privacy;

import com.pte.chat.internal.constant.ChatConstants;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and hides the two kinds of contact detail that are easy to spot reliably: email addresses and phone
 * numbers. A question is masked before it is stored or sent to the AI provider, so what a student types by
 * mistake does not leave the system. This is a safety net, not a privacy filter: names, addresses and other
 * free text are not detected, and only phone numbers written the usual Vietnamese way are, so a number spelt
 * some other way can slip through.
 */
public final class ChatContactDetails {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");
    /** A run of digits that may be split by spaces, dots, dashes or brackets, as phone numbers usually are. */
    private static final Pattern DIGIT_RUN = Pattern.compile("[+(]?\\d[\\d ().\\-]{6,}\\d");
    private static final int MIN_PHONE_DIGITS = 9;
    private static final int MIN_GROUP_DIGITS = 3;
    private static final int MAX_CODE_DIGITS = 3;
    private static final String VIETNAM_CODE = "84";
    /** "84" plus a nine or ten digit national number, written as one block. */
    private static final int MIN_FULL_NUMBER_DIGITS = 11;

    private ChatContactDetails() {
    }

    public static boolean contains(String text) {
        if (EMAIL.matcher(text).find()) {
            return true;
        }
        Matcher run = DIGIT_RUN.matcher(text);
        while (run.find()) {
            if (isPhoneNumber(run.group())) {
                return true;
            }
        }
        return false;
    }

    public static String mask(String text) {
        String withoutEmails = EMAIL.matcher(text).replaceAll(ChatConstants.MASK_EMAIL);
        return DIGIT_RUN.matcher(withoutEmails).replaceAll(match ->
                isPhoneNumber(match.group()) ? Matcher.quoteReplacement(ChatConstants.MASK_PHONE) : Matcher.quoteReplacement(match.group()));
    }

    /**
     * A digit run is a phone number only if it is shaped like one: a leading 0 or bracket, or a country code
     * (+NN or 84), with every other group of at least three digits. Years ("2023-2024"), dates ("12-05-2004",
     * groups of two) and plain lists of numbers fail this test, so they reach the assistant untouched.
     */
    private static boolean isPhoneNumber(String run) {
        if (run.chars().filter(Character::isDigit).count() < MIN_PHONE_DIGITS) {
            return false;
        }
        String[] groups = Arrays.stream(run.split("\\D+")).filter(group -> !group.isEmpty()).toArray(String[]::new);
        boolean countryCode = run.startsWith("+") || groups[0].equals(VIETNAM_CODE)
                || (groups[0].startsWith(VIETNAM_CODE) && groups[0].length() >= MIN_FULL_NUMBER_DIGITS);
        if (!countryCode && !run.startsWith("0") && !run.startsWith("(")) {
            return false;
        }
        for (int i = 0; i < groups.length; i++) {
            boolean codeGroup = countryCode && i == 0 && groups[i].length() <= MAX_CODE_DIGITS;
            if (!codeGroup && groups[i].length() < MIN_GROUP_DIGITS) {
                return false;
            }
        }
        return true;
    }
}
