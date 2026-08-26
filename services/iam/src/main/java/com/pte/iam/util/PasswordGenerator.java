package com.pte.iam.util;

import java.security.SecureRandom;

/**
 * Generates a temporary password for bulk-created accounts, in a fixed,
 * printable/typeable shape: 8 characters grouped {@code XXXX-XXXX}, drawn from
 * an unambiguous alphanumeric charset (excludes {@code 0/O} and {@code 1/l/I},
 * which are commonly confused on a printed credentials handout). The shape is
 * standardized; every character is still independently random via
 * {@link SecureRandom} — never derived from the account's own data.
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
