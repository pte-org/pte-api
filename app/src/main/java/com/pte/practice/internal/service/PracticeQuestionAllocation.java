package com.pte.practice.internal.service;

/** Splits a total question count across task types for a skill session. */
final class PracticeQuestionAllocation {

    private PracticeQuestionAllocation() {
    }

    /**
     * Even split of {@code total} over {@code taskCount} task types; the remainder goes to the first types, so the
     * parts always add up to {@code total} (10 over 3 is 4, 3, 3; 2 over 3 is 1, 1, 0).
     */
    static int[] distribute(int total, int taskCount) {
        if (total < 0 || taskCount <= 0) {
            throw new IllegalArgumentException("total must be >= 0 and taskCount > 0");
        }
        int[] parts = new int[taskCount];
        int base = total / taskCount;
        int remainder = total % taskCount;
        for (int index = 0; index < taskCount; index++) {
            parts[index] = base + (index < remainder ? 1 : 0);
        }
        return parts;
    }
}
