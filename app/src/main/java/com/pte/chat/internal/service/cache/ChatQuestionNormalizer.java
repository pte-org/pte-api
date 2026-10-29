package com.pte.chat.internal.service.cache;

import java.text.Normalizer;
import java.util.Locale;

public final class ChatQuestionNormalizer {

    private ChatQuestionNormalizer() {
    }

    public static String normalize(String question) {
        if (question == null) {
            return "";
        }
        String folded = Normalizer.normalize(question, Normalizer.Form.NFKC);
        return folded.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
