package com.pte.overview.internal.service;

import java.util.function.Supplier;

/**
 * Per-request memo for a value several dashboard sections read. The first call
 * computes it; later calls reuse the value, or rethrow the same failure, so each
 * section still fails on its own while the source is queried once. Sections run
 * sequentially within a request, so it is intentionally not thread-safe.
 */
public final class Memo<T> implements Supplier<T> {

    private final Supplier<T> source;
    private boolean resolved;
    private T value;
    private RuntimeException failure;

    public Memo(Supplier<T> source) {
        this.source = source;
    }

    @Override
    public T get() {
        if (!resolved) {
            try {
                value = source.get();
            } catch (RuntimeException e) {
                failure = e;
            }
            resolved = true;
        }
        if (failure != null) {
            throw failure;
        }
        return value;
    }
}
