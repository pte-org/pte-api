package com.pte.itembank.internal.generation.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Hash used to stop the generator from producing two identical questions: lower-case, keep only letters and digits,
 * collapse spaces, then SHA-256. Paraphrases and hand-written questions are deliberately not covered.
 */
public final class ContentHasher {

    private static final String NOT_LETTER_OR_DIGIT_OR_SPACE = "[^\\p{L}\\p{N}\\s]";
    private static final String WHITESPACE_RUN = "\\s+";
    private static final String ALGORITHM = "SHA-256";

    private ContentHasher() {
    }

    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll(NOT_LETTER_OR_DIGIT_OR_SPACE, "")
                .replaceAll(WHITESPACE_RUN, " ")
                .trim();
    }

    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM)
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
