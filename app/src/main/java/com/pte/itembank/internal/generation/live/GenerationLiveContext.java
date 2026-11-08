package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.llm.TokenSink;

import java.util.UUID;

/**
 * The item the current worker thread is making, so the gateway can stream the agents' text to the live view without
 * every generator and reviewer passing it along. The pipeline opens it around one item and always closes it; a
 * provider call outside an item (or on another thread) finds nothing and is not streamed.
 */
public final class GenerationLiveContext {

    private record Slot(GenerationLiveFeed feed, UUID jobPublicId, int index) {
    }

    private static final ThreadLocal<Slot> CURRENT = new ThreadLocal<>();

    private GenerationLiveContext() {
    }

    public static void open(GenerationLiveFeed feed, UUID jobPublicId, int index) {
        CURRENT.set(new Slot(feed, jobPublicId, index));
    }

    public static void close() {
        CURRENT.remove();
    }

    /** @return where the agent's text goes, or {@link TokenSink#NONE} when no item is being made on this thread */
    public static TokenSink sinkFor(LlmRole agent) {
        Slot slot = CURRENT.get();
        return slot == null ? TokenSink.NONE : slot.feed().sink(slot.jobPublicId(), slot.index(), agent);
    }
}
