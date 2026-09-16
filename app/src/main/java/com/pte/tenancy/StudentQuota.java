package com.pte.tenancy;

/** Immutable student-capacity snapshot for one tenant. */
public record StudentQuota(long current, long limit) {

    public long remaining() {
        return Math.max(0L, limit - current);
    }

    public long remainingAfter(long adding) {
        return Math.max(0L, remaining() - Math.max(0L, adding));
    }

    public boolean canAdd(long adding) {
        return adding >= 0L && current <= limit && adding <= remaining();
    }
}
