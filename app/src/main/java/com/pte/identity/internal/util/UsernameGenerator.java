package com.pte.identity.internal.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Generates an organization account username (Student or staff) as {@code {tenant.code}.{e-mail prefix}}, for
 * example {@code ninh-le.leninh2005}.
 *
 * <p>The prefix is the part of the account's e-mail before the {@code @}, lower-cased and reduced to letters and
 * digits, at most {@value #MAX_PREFIX_LENGTH} characters. A name already taken gets a number ({@code ...2},
 * {@code ...3}). An e-mail with nothing usable before the {@code @}, or too many clashes, falls back to
 * {@code {tenant.code}.{random}}: 8 characters from a 31-letter alphabet (about 40 bits).
 *
 * <p>A readable username is easier to guess than a random one, which is acceptable because sign-in also needs the
 * password and is throttled per username and address. Callers check that the result is unused (or retry on a
 * unique-key error).
 */
public final class UsernameGenerator {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o/1/i/l
    private static final int RANDOM_LENGTH = 8;
    static final int MAX_PREFIX_LENGTH = 20;
    /** Highest number tried after the plain e-mail prefix before giving up on a readable name. */
    private static final int MAX_NUMBERED = 20;
    /** Attempts to find an unused username; shared by the single-create, Host and bulk roster paths. */
    public static final int COLLISION_RETRIES = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private UsernameGenerator() {
    }

    /**
     * A generated username for which {@code taken} is false, trying a few times. The odds of a clash are tiny, so
     * running out of attempts is reported as an {@link IllegalStateException} rather than handled.
     */
    public static String generateUnused(String tenantCode, Predicate<String> taken) {
        for (int attempt = 0; attempt < COLLISION_RETRIES; attempt++) {
            String candidate = generate(tenantCode);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate an unused username for " + tenantCode);
    }

    /**
     * A readable username from {@code email} for which {@code taken} is false: the plain prefix, then numbered
     * variants, then a random one. Without a usable e-mail it is the random form straight away.
     */
    public static String generateUnused(String tenantCode, String email, Predicate<String> taken) {
        String prefix = emailPrefix(email);
        if (prefix != null) {
            String base = tenantCode + "." + prefix;
            if (!taken.test(base)) {
                return base;
            }
            for (int number = 2; number <= MAX_NUMBERED; number++) {
                String candidate = base + number;
                if (!taken.test(candidate)) {
                    return candidate;
                }
            }
        }
        return generateUnused(tenantCode, taken);
    }

    /** Letters and digits before the {@code @}, lower-case and without accents; null when nothing is left. */
    static String emailPrefix(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        String plain = Normalizer.normalize(local.trim().toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD);
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < plain.length() && prefix.length() < MAX_PREFIX_LENGTH; i++) {
            char c = plain.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                prefix.append(c);
            }
        }
        return prefix.isEmpty() ? null : prefix.toString();
    }

    public static String generate(String tenantCode) {
        StringBuilder suffix = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return tenantCode + "." + suffix;
    }
}
