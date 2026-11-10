package com.pte.assessment.internal.service.assembly;

import java.util.Random;

/**
 * Turns a seed into a {@link Random}. {@code new Random(1)}, {@code new Random(2)}... start with nearly the same
 * values, so small consecutive seeds would give nearly the same exam; the seed is mixed first (SplitMix64) so any
 * two different seeds give unrelated draws. The same seed still always gives the same draw.
 */
final class Seeds {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_2 = 0x94D049BB133111EBL;
    private static final int SHIFT_1 = 30;
    private static final int SHIFT_2 = 27;
    private static final int SHIFT_3 = 31;

    private Seeds() {
    }

    static Random random(long seed) {
        long z = seed + GOLDEN_GAMMA;
        z = (z ^ (z >>> SHIFT_1)) * MIX_1;
        z = (z ^ (z >>> SHIFT_2)) * MIX_2;
        return new Random(z ^ (z >>> SHIFT_3));
    }
}
