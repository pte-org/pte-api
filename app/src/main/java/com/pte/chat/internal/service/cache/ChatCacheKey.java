package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.constant.ChatConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChatCacheKey {

    private ChatCacheKey() {
    }

    public static String of(String question, String language, String taskType, String promptVersion) {
        StringBuilder input = new StringBuilder();
        for (String field : new String[]{ChatQuestionNormalizer.normalize(question), nullToEmpty(language),
                nullToEmpty(taskType), nullToEmpty(promptVersion)}) {
            input.append(field.length()).append(':').append(field).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ChatConstants.SHA256_UNAVAILABLE_MESSAGE, ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
