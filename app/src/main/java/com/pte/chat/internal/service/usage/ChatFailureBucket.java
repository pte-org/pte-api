package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiFailureReason;

/**
 * The groups a failed message is counted under on the admin screen. Several provider reasons that an admin would
 * act on the same way share one group; anything that does not fit, and a failure with no known reason, is OTHER.
 */
public enum ChatFailureBucket {

    TIMEOUT,
    RATE_LIMITED,
    AUTH,
    UNAVAILABLE,
    TRUNCATED,
    OTHER;

    public static ChatFailureBucket of(ChatAiFailureReason reason) {
        if (reason == null) {
            return OTHER;
        }
        return switch (reason) {
            case TIMEOUT -> TIMEOUT;
            case RATE_LIMITED -> RATE_LIMITED;
            case AUTH_FAILED -> AUTH;
            case UNAVAILABLE, NETWORK_ERROR -> UNAVAILABLE;
            case TRUNCATED -> TRUNCATED;
            case BAD_REQUEST, CONTENT_BLOCKED, EMPTY_RESPONSE, INVALID_RESPONSE -> OTHER;
        };
    }
}
