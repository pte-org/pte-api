package com.pte.scheduling.domain;

import com.pte.scheduling.domain.enums.ReplayPolicyType;

/**
 * Immutable replay-limit value, decoupled from its two-column {@link ExamPolicy}
 * storage so callers never compare raw type+limit pairs by hand.
 */
public final class ReplayPolicy {

    private final ReplayPolicyType type;
    private final Integer limit;

    private ReplayPolicy(ReplayPolicyType type, Integer limit) {
        this.type = type;
        this.limit = limit;
    }

    public static ReplayPolicy unlimited() {
        return new ReplayPolicy(ReplayPolicyType.UNLIMITED, null);
    }

    public static ReplayPolicy limited(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Limited replay count must be >= 1");
        }
        return new ReplayPolicy(ReplayPolicyType.LIMITED, count);
    }

    public static ReplayPolicy of(ReplayPolicyType type, Integer limit) {
        return type == ReplayPolicyType.UNLIMITED ? unlimited() : limited(limit);
    }

    public ReplayPolicyType type() {
        return type;
    }

    /** Null when {@link #type()} is UNLIMITED. */
    public Integer limit() {
        return limit;
    }

    public boolean isUnlimited() {
        return type == ReplayPolicyType.UNLIMITED;
    }
}
