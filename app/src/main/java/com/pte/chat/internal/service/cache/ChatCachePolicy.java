package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.service.privacy.ChatContactDetails;

/**
 * Decides whether a question may enter the answer cache shared by all students. A cached answer is replayed to
 * strangers and may repeat what the student typed, so a long question (likely a pasted essay) or one with an
 * email address or phone number is answered normally and never stored for others. This is a safety net for
 * the common cases, not a privacy filter: names and other free text are not detected.
 */
public final class ChatCachePolicy {

    private ChatCachePolicy() {
    }

    /** {@code question} is what the student typed, before any masking. */
    public static boolean cacheable(String question, int maxChars) {
        return question.length() <= maxChars && !ChatContactDetails.contains(question);
    }
}
