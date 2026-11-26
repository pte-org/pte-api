package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Speaks a text and makes its length fit the task type: one more try at a speed that moves the length toward the
 * range, then the length error. Shared by the generator and by changing the voice of a saved question.
 */
@Component
public class SpeechFitter {

    private static final double SPEED_STEP = 0.1;

    /** The spoken audio, the speed it was spoken at and its length in seconds. */
    public record FittedSpeech(byte[] audio, double speed, double seconds) { }

    private final KokoroSpeechClient speech;
    private final AudioDurationProbe probe;

    public SpeechFitter(KokoroSpeechClient speech, AudioDurationProbe probe) {
        this.speech = speech;
        this.probe = probe;
    }

    public FittedSpeech fit(String text, String voice, String taskTypeKey) {
        double speed = AudioDurationRules.baseSpeed(taskTypeKey);
        byte[] audio = speech.synthesize(text, voice, speed);
        Optional<AudioDurationRules.Range> rule = AudioDurationRules.forTask(taskTypeKey);
        double seconds = probe.durationSeconds(audio);
        if (rule.isPresent() && !rule.get().contains(seconds)) {
            speed = rule.get().isTooShort(seconds) ? speed - SPEED_STEP : speed + SPEED_STEP;
            audio = speech.synthesize(text, voice, speed);
            seconds = probe.durationSeconds(audio);
            if (!rule.get().contains(seconds)) {
                throw durationOutOfRange(seconds, rule.get());
            }
        }
        return new FittedSpeech(audio, speed, seconds);
    }

    static GenerationMediaException durationOutOfRange(double seconds, AudioDurationRules.Range range) {
        return new GenerationMediaException(QuestionGenerationConstants.GENERATION_AUDIO_DURATION_OUT_OF_RANGE,
                "audio " + Math.round(seconds * 10.0) / 10.0 + "s, expected " + range.min() + "-" + range.max() + "s");
    }
}
