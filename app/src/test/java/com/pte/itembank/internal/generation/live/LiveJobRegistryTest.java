package com.pte.itembank.internal.generation.live;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveJobRegistryTest {

    private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");

    private final LiveJobRegistry registry = new LiveJobRegistry(Clock.fixed(T0, ZoneOffset.UTC));
    private final UUID jobId = UUID.randomUUID();

    @Test
    void aJobRunAgainAfterItsRunEndedStartsAFreshLiveState() {
        LiveJob paused = registry.open(jobId);
        paused.finish(T0);

        LiveJob resumed = registry.start(jobId);

        assertThat(resumed).isNotSameAs(paused);
        assertThat(resumed.isFinished()).isFalse();
        assertThat(registry.open(jobId)).isSameAs(resumed);
    }

    @Test
    void aJobStillRunningKeepsItsLiveState() {
        LiveJob running = registry.open(jobId);

        assertThat(registry.start(jobId)).isSameAs(running);
    }

    @Test
    void aWatcherOfAFinishedJobStillFindsItsLastState() {
        LiveJob finished = registry.open(jobId);
        finished.finish(T0);

        assertThat(registry.open(jobId)).isSameAs(finished);
    }
}
