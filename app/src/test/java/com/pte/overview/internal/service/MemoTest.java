package com.pte.overview.internal.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoTest {

    @Test
    void sourceIsComputedOnceAndEveryGetReturnsTheSameValue() {
        AtomicInteger calls = new AtomicInteger();
        Object value = new Object();
        Memo<Object> memo = new Memo<>(() -> {
            calls.incrementAndGet();
            return value;
        });

        assertThat(memo.get()).isSameAs(value);
        assertThat(memo.get()).isSameAs(value);
        assertThat(memo.get()).isSameAs(value);
        assertThat(calls).hasValue(1);
    }

    @Test
    void sourceIsNotCalledUntilTheFirstGet() {
        AtomicInteger calls = new AtomicInteger();

        new Memo<>(() -> calls.incrementAndGet());

        assertThat(calls).hasValue(0);
    }

    @Test
    void aFailureIsRethrownIdenticallyOnEveryGetWithTheSourceCalledOnce() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("enrollment down");
        Memo<String> memo = new Memo<>(() -> {
            calls.incrementAndGet();
            throw failure;
        });

        assertThatThrownBy(memo::get).isSameAs(failure);
        assertThatThrownBy(memo::get).isSameAs(failure);
        assertThatThrownBy(memo::get).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @Test
    void aNullResultIsMemoisedAsWellAndNotRecomputed() {
        AtomicInteger calls = new AtomicInteger();
        Memo<String> memo = new Memo<>(() -> {
            calls.incrementAndGet();
            return null;
        });

        assertThat(memo.get()).isNull();
        assertThat(memo.get()).isNull();
        assertThat(calls).hasValue(1);
    }

    @Test
    void aSuccessfulValueIsNeverReplacedByALaterDifferentSourceResult() {
        AtomicInteger counter = new AtomicInteger();
        Memo<Integer> memo = new Memo<>(counter::incrementAndGet);

        assertThat(memo.get()).isEqualTo(1);
        assertThat(memo.get()).isEqualTo(1);
    }
}
