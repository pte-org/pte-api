package com.pte.itembank.internal.generation.stop;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A person asked to stop one running job (a pause or a cancel). Everything on the job's thread can see it.
 *
 * <p>The interrupt flag alone is not enough. A library can clear it, and the retry and fallback of a provider call
 * would read an interrupted call as an outage and ask again. So every provider call checks this signal before it
 * starts and when it fails, and a stop closes the response being read so the call ends at once. Why the job stops
 * (pause or cancel) is still read from the job row, where a cancel wins.
 */
public final class GenerationStop {

    /** Ends the watch of one response; never throws. */
    @FunctionalInterface
    public interface Watch extends AutoCloseable {
        @Override
        void close();
    }

    private static final Watch NO_WATCH = () -> {
    };
    private static final ThreadLocal<GenerationStop> CURRENT = new ThreadLocal<>();

    private volatile boolean requested;
    private final AtomicReference<Closeable> inFlight = new AtomicReference<>();

    /** Makes this the stop signal of the calling thread, for the job it is about to run. */
    public void bind() {
        CURRENT.set(this);
    }

    public static void unbind() {
        CURRENT.remove();
    }

    /** Called from the thread that took the request; the job's thread stops at its next check or read. */
    public void request() {
        requested = true;
        closeQuietly(inFlight.getAndSet(null));
    }

    /** @return true when the job on the calling thread was asked to stop; false outside a job */
    public static boolean isRequested() {
        GenerationStop stop = CURRENT.get();
        return stop != null && stop.requested;
    }

    public static void throwIfRequested() {
        if (isRequested()) {
            throw new GenerationStoppedException();
        }
    }

    /**
     * Watches the response the calling thread reads, so a stop closes it and the read ends at once. Close the watch
     * when the read is over. Outside a job nothing is watched.
     */
    public static Watch watch(Closeable response) {
        GenerationStop stop = CURRENT.get();
        if (stop == null) {
            return NO_WATCH;
        }
        stop.inFlight.set(response);
        if (stop.requested) {
            // The stop came between the request and this watch: it found nothing to close.
            closeQuietly(stop.inFlight.getAndSet(null));
            throw new GenerationStoppedException();
        }
        return () -> stop.inFlight.compareAndSet(response, null);
    }

    private static void closeQuietly(Closeable response) {
        if (response == null) {
            return;
        }
        try {
            response.close();
        } catch (IOException | RuntimeException ignored) {
            // The reading thread sees the stop either way; a response that fails to close changes nothing.
        }
    }
}
