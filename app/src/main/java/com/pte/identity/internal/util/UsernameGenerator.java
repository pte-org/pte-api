package com.pte.identity.internal.util;

import java.security.SecureRandom;

/**
 * Generates a STUDENT username as {@code {tenant.code}.{random}}. Not called
 * yet (plans/quang-tenant-commercialization Phase 1 only adds this utility;
 * Phase 8's roster import is what actually wires it into student creation).
 *
 * <p>The random part is not derived from any row data (name, DOB, etc.) — a
 * predictable username would let one student guess another's account in the
 * same tenant.
 */
public final class UsernameGenerator {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o/1/i/l
    private static final int RANDOM_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private UsernameGenerator() {
    }

    public static String generate(String tenantCode) {
        StringBuilder suffix = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return tenantCode + "." + suffix;
    }
}
