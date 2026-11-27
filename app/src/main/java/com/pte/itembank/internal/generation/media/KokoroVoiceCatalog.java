package com.pte.itembank.internal.generation.media;

import java.util.List;
import java.util.Optional;

/**
 * Every English Kokoro voice the generator may speak with. A constant list on purpose: the deployment can only switch
 * voices off (see {@link EnabledVoices}), because adding one needs a label and an accent that code has to know.
 */
public final class KokoroVoiceCatalog {

    public enum Accent { US, UK }

    public enum Gender { FEMALE, MALE }

    /** One voice: the Kokoro code sent to the speech service, and what a person sees in a picker. */
    public record Voice(String code, Accent accent, Gender gender, String label) { }

    private static final List<Voice> ALL = List.of(
            new Voice("af_heart", Accent.US, Gender.FEMALE, "Heart"),
            new Voice("af_bella", Accent.US, Gender.FEMALE, "Bella"),
            new Voice("af_nicole", Accent.US, Gender.FEMALE, "Nicole"),
            new Voice("af_sarah", Accent.US, Gender.FEMALE, "Sarah"),
            new Voice("af_sky", Accent.US, Gender.FEMALE, "Sky"),
            new Voice("am_michael", Accent.US, Gender.MALE, "Michael"),
            new Voice("am_adam", Accent.US, Gender.MALE, "Adam"),
            new Voice("am_eric", Accent.US, Gender.MALE, "Eric"),
            new Voice("am_liam", Accent.US, Gender.MALE, "Liam"),
            new Voice("am_onyx", Accent.US, Gender.MALE, "Onyx"),
            new Voice("bf_emma", Accent.UK, Gender.FEMALE, "Emma"),
            new Voice("bf_alice", Accent.UK, Gender.FEMALE, "Alice"),
            new Voice("bf_isabella", Accent.UK, Gender.FEMALE, "Isabella"),
            new Voice("bf_lily", Accent.UK, Gender.FEMALE, "Lily"),
            new Voice("bm_george", Accent.UK, Gender.MALE, "George"),
            new Voice("bm_daniel", Accent.UK, Gender.MALE, "Daniel"),
            new Voice("bm_fable", Accent.UK, Gender.MALE, "Fable"),
            new Voice("bm_lewis", Accent.UK, Gender.MALE, "Lewis"));

    private KokoroVoiceCatalog() { }

    public static List<Voice> all() {
        return ALL;
    }

    public static Optional<Voice> find(String code) {
        return ALL.stream().filter(voice -> voice.code().equals(code)).findFirst();
    }
}
