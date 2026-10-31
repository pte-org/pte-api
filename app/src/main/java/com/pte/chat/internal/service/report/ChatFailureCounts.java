package com.pte.chat.internal.service.report;

/**
 * How many of a day's failed messages failed for each reason. Counts only; they add up to the day's {@code failed}.
 * {@code other} holds the reasons not worth a column of their own and every failure counted before reasons were kept.
 */
public record ChatFailureCounts(int timeout, int rateLimited, int authFailed, int unavailable, int truncated,
                                int other) {

    public static final ChatFailureCounts NONE = new ChatFailureCounts(0, 0, 0, 0, 0, 0);
}
