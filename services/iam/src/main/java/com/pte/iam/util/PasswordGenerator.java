package com.pte.iam.util;

import java.security.SecureRandom;

/**
 * Generates a temporary password in a fixed, typeable shape ({@code XXXX-XXXX})
 * from an unambiguous charset (no {@code 0/O}, {@code 1/l/I}) — every character
 * is still independently drawn via {@link SecureRandom}.
 */
public final class PasswordGenerator {

    private static final String CHARSET = "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
    private static final int LENGTH = 8;
    private static final int GROUP_SIZE = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generateReadable() {
        StringBuilder builder = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == GROUP_SIZE) {
                builder.append('-');
            }
            builder.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return builder.toString();
    }
}
