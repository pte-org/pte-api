package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechFitterTest {

    private final KokoroSpeechClient kokoro = mock(KokoroSpeechClient.class);
    private final AudioDurationProbe probe = mock(AudioDurationProbe.class);
    private final SpeechFitter fitter = new SpeechFitter(kokoro, probe);
    private final byte[] audio = {1};

    @Test
    void aLengthInsideTheRangeIsSpokenOnce() {
        when(kokoro.synthesize(eq("hi"), eq("af_heart"), anyDouble())).thenReturn(audio);
        when(probe.durationSeconds(any())).thenReturn(5.0);

        SpeechFitter.FittedSpeech speech = fitter.fit("hi", "af_heart", "REPEAT_SENTENCE");

        assertThat(speech.seconds()).isEqualTo(5.0);
        assertThat(speech.speed()).isEqualTo(AudioDurationRules.baseSpeed("REPEAT_SENTENCE"));
        verify(kokoro, times(1)).synthesize(eq("hi"), eq("af_heart"), anyDouble());
    }

    @Test
    void aTooShortAudioIsSpokenOnceMoreSlower() {
        when(kokoro.synthesize(eq("hi"), eq("af_heart"), anyDouble())).thenReturn(audio);
        when(probe.durationSeconds(any())).thenReturn(1.0, 4.0);

        SpeechFitter.FittedSpeech speech = fitter.fit("hi", "af_heart", "REPEAT_SENTENCE");

        assertThat(speech.speed()).isLessThan(AudioDurationRules.baseSpeed("REPEAT_SENTENCE"));
        assertThat(speech.seconds()).isEqualTo(4.0);
    }

    @Test
    void aLengthThatIsStillWrongAfterTheRetryIsAnError() {
        when(kokoro.synthesize(eq("hi"), eq("af_heart"), anyDouble())).thenReturn(audio);
        when(probe.durationSeconds(any())).thenReturn(1.0, 1.5);

        assertThatThrownBy(() -> fitter.fit("hi", "af_heart", "REPEAT_SENTENCE"))
                .isInstanceOf(GenerationMediaException.class);
    }
}
