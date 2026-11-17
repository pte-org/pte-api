package com.pte.identity.internal.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Generates an organization account username (Student or staff) as {@code {tenant.code}.{random}}.
 *
 * <p>The random part is not derived from any row data (name, DOB, etc.) — a
 * predictable username would let one person guess another's account in the
 * same tenant. It is 8 characters from a 31-letter alphabet (about 40 bits), so a clash is very unlikely but
 * possible: callers check that the result is unused (or retry on a unique-key error).
 */
public final class UsernameGenerator {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o/1/i/l
    private static final int RANDOM_LENGTH = 8;
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

    public static String generate(String tenantCode) {
        StringBuilder suffix = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return tenantCode + "." + suffix;
    }
}
