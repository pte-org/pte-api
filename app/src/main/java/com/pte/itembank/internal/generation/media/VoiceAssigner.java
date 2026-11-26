package com.pte.itembank.internal.generation.media;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * One voice per position of a batch's audio questions, a pure function of the batch seed and the position, so a job
 * that runs again speaks with the same voice. Accents alternate on every step and the gender every two steps, so
 * neighbours never share a voice and each accent hears women and men in turn.
 */
@Component
public class VoiceAssigner {

    private static final int STEPS_PER_GENDER = 2;
    private static final int POSITIONS_PER_VOICE_TURN = 4;

    private final EnabledVoices enabled;

    public VoiceAssigner(EnabledVoices enabled) {
        this.enabled = enabled;
    }

    /**
     * The voice for {@code position} (0 or more). With both accents enabled neighbours always differ; when one accent
     * is switched off, neighbours share an accent and differ only through gender and the shuffled order.
     */
    public String assign(long seed, int position) {
        boolean startsUk = new SplittableRandom(seed).nextBoolean();
        KokoroVoiceCatalog.Accent accent = startsUk ^ (position % 2 == 1)
                ? KokoroVoiceCatalog.Accent.UK : KokoroVoiceCatalog.Accent.US;
        KokoroVoiceCatalog.Gender gender = (position / STEPS_PER_GENDER) % 2 == 0
                ? KokoroVoiceCatalog.Gender.FEMALE : KokoroVoiceCatalog.Gender.MALE;
        List<KokoroVoiceCatalog.Voice> group = shuffled(seed, accent, gender);
        if (group.isEmpty()) {
            group = shuffled(seed, accent, other(gender));
        }
        if (group.isEmpty()) {
            // The whole accent is switched off: the other accent speaks instead.
            KokoroVoiceCatalog.Accent otherAccent = accent == KokoroVoiceCatalog.Accent.US
                    ? KokoroVoiceCatalog.Accent.UK : KokoroVoiceCatalog.Accent.US;
            group = shuffled(seed, otherAccent, gender);
            if (group.isEmpty()) {
                group = shuffled(seed, otherAccent, other(gender));
            }
        }
        return group.get((position / POSITIONS_PER_VOICE_TURN) % group.size()).code();
    }

    private static KokoroVoiceCatalog.Gender other(KokoroVoiceCatalog.Gender gender) {
        return gender == KokoroVoiceCatalog.Gender.FEMALE
                ? KokoroVoiceCatalog.Gender.MALE : KokoroVoiceCatalog.Gender.FEMALE;
    }

    /** The enabled voices of one accent and gender in an order the seed decides (Fisher-Yates). */
    private List<KokoroVoiceCatalog.Voice> shuffled(long seed, KokoroVoiceCatalog.Accent accent,
                                                    KokoroVoiceCatalog.Gender gender) {
        List<KokoroVoiceCatalog.Voice> group = new ArrayList<>(enabled.of(accent).stream()
                .filter(voice -> voice.gender() == gender).toList());
        SplittableRandom random = new SplittableRandom(seed + accent.ordinal() * 31L + gender.ordinal());
        for (int i = group.size() - 1; i > 0; i--) {
            Collections.swap(group, i, random.nextInt(i + 1));
        }
        return group;
    }
}
