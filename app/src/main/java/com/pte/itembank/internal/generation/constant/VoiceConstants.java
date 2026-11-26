package com.pte.itembank.internal.generation.constant;

import java.util.Set;

/** Codes, limits and fixed texts of choosing the voice of a spoken question. */
public final class VoiceConstants {

    public static final String GENERATION_VOICE_UNKNOWN = "GENERATION_VOICE_UNKNOWN";
    public static final String GENERATION_VOICE_NOT_SUPPORTED_FOR_TASK = "GENERATION_VOICE_NOT_SUPPORTED_FOR_TASK";
    public static final String GENERATION_VOICE_UNCHANGED = "GENERATION_VOICE_UNCHANGED";
    public static final String GENERATION_VOICE_BUSY = "GENERATION_VOICE_BUSY";

    /** Task types whose whole spoken text is kept in the question's reference answer, so they can be spoken again. */
    public static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            "WRITE_FROM_DICTATION", "REPEAT_SENTENCE", "ANSWER_SHORT_QUESTION", "SUMMARIZE_SPOKEN_TEXT",
            "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD",
            "FILL_IN_THE_BLANKS_TYPE_IN", "RE_TELL_LECTURE", "HIGHLIGHT_INCORRECT_WORDS");

    /** What a person hears when trying a voice out. */
    public static final String SAMPLE_TEXT = "The library opens at nine o'clock, and the lecture begins shortly after.";
    public static final double SAMPLE_SPEED = 1.0;

    /** Kokoro calls people can have running at once across the whole app (changing a voice, trying one out). */
    public static final int VOICE_CHANGE_MAX_CONCURRENT = 2;
    public static final long SAMPLE_FAILURE_CACHE_SECONDS = 60;
    public static final String SAMPLE_CACHE_CONTROL = "private, max-age=86400";

    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_PROMPT = "AUDIO_PROMPT";
    public static final String META_MEDIA = "media";
    public static final String META_VOICE = "voice";
    public static final String META_SPEED = "speed";
    public static final String META_DURATION_SEC = "durationSec";
    public static final String META_AUDIO_PROMPT_REF = "audioPromptRef";
    public static final double SECONDS_ROUNDING = 10.0;

    private VoiceConstants() { }
}
