package com.pte.itembank.internal.generation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Where one requested question is in the pipeline; the web timeline renders exactly these. */
public enum ItemStage {
    /** READY: written, reviewed and its media made, but not saved: a job started from the question form. */
    QUEUED, GENERATING, VALIDATING, REVIEWING, RENDERING_MEDIA, READY, SAVED, FAILED, CANCELLED;

    public static final Set<ItemStage> TERMINAL = EnumSet.of(READY, SAVED, FAILED, CANCELLED);
    public static final Set<ItemStage> OPEN = EnumSet.complementOf(EnumSet.copyOf(TERMINAL));

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
