package com.pte.itembank.internal.generation.media;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Selects voice identifiers for audio generation: from the batch's shared sequence when the job belongs to a batch,
 * otherwise on its own, alternating accents within the job. Seed-based, so a run can be reproduced.
 */
@Component
public class VoicePicker {

    private final EnabledVoices enabled;
    private final VoiceAssigner assigner;

    public VoicePicker(EnabledVoices enabled, VoiceAssigner assigner) {
        this.enabled = enabled;
        this.assigner = assigner;
    }

    /**
     * Pick a voice for an item of a batch: the batch's own sequence decides it, so the questions of one batch do not
     * repeat each other's voice. A job outside any batch ({@code position} null) picks on its own, as before.
     */
    public String pick(int itemIndex, long seed, BatchPosition position) {
        return position == null ? pick(itemIndex, seed) : assigner.assign(position.seed(), position.offset() + itemIndex);
    }

    /**
     * Pick a voice for the given item index within a job.
     * Alternates between US and UK accents; within each accent, rotates through available voices.
     *
     * @param itemIndex 0-based index of item in the job
     * @param seed job seed for reproducibility
     * @return voice identifier (e.g., "af_heart")
     */
    public String pick(int itemIndex, long seed) {
        // The seed picks the accent the job starts with: a batch runs one item per job, so alternating on the item
        // index alone gave every batch question an American voice. SplittableRandom mixes nearby seeds well, which
        // java.util.Random does not on its first draw.
        boolean startsUk = new SplittableRandom(seed).nextBoolean();
        boolean uk = startsUk ^ (itemIndex % 2 == 1);
        List<KokoroVoiceCatalog.Voice> voices = enabled.of(uk ? KokoroVoiceCatalog.Accent.UK : KokoroVoiceCatalog.Accent.US);
        if (voices.isEmpty()) {
            // The other accent: a deployment may switch one accent off entirely.
            voices = enabled.of(uk ? KokoroVoiceCatalog.Accent.US : KokoroVoiceCatalog.Accent.UK);
        }
        return voices.get(new SplittableRandom(seed + itemIndex).nextInt(voices.size())).code();
    }
}
