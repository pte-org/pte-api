package com.pte;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** A clock frozen at the moment the test JVM first needs it, so time-dependent tests are repeatable within a run. */
public final class TestClocks {

    private static final Clock FIXED = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    private TestClocks() {
    }

    public static Clock fixed() {
        return FIXED;
    }
}
