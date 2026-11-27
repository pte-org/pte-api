package com.pte.itembank.internal.generation.stop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationStopTest {

    private final AtomicInteger closed = new AtomicInteger();
    private final Closeable response = closed::incrementAndGet;

    @AfterEach
    void unbind() {
        GenerationStop.unbind();
    }

    @Test
    void outsideAJobNothingIsEverStopped() {
        assertThat(GenerationStop.isRequested()).isFalse();
        GenerationStop.throwIfRequested();
        GenerationStop.watch(response).close();
        assertThat(closed).hasValue(0);
    }

    @Test
    void aStopClosesTheResponseBeingRead() {
        GenerationStop stop = new GenerationStop();
        stop.bind();
        GenerationStop.watch(response);

        stop.request();

        assertThat(closed).hasValue(1);
        assertThat(GenerationStop.isRequested()).isTrue();
        assertThatThrownBy(GenerationStop::throwIfRequested).isInstanceOf(GenerationStoppedException.class);
    }

    @Test
    void aResponseNoLongerWatchedIsNotClosed() {
        GenerationStop stop = new GenerationStop();
        stop.bind();
        GenerationStop.watch(response).close();

        stop.request();

        assertThat(closed).hasValue(0);
    }

    @Test
    void aStopThatCameBeforeTheWatchClosesTheResponseAtOnce() {
        GenerationStop stop = new GenerationStop();
        stop.bind();
        stop.request();

        assertThatThrownBy(() -> GenerationStop.watch(response)).isInstanceOf(GenerationStoppedException.class);
        assertThat(closed).hasValue(1);
    }
}
