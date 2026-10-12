package com.pte.scoring.internal.vendor.audio;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

class AudioNormalizerTest {

    @Test
    void downmixesAndResamplesA44kStereoRecordingTo16kMono() {
        short[] channel = TestAudio.tone(3, 44_100, 440, 8_000);

        PcmAudio audio = AudioNormalizer.normalize(
                TestAudio.wav(TestAudio.interleave(channel, channel), 44_100, 2));

        assertThat(audio.sampleRate()).isEqualTo(16_000);
        assertThat(audio.samples().length).isCloseTo(48_000, within(2));
        assertThat(audio.durationMs()).isCloseTo(3_000L, within(1L));
        assertThat(TestAudio.rms(audio.samples())).isCloseTo(TestAudio.rms(channel), withinPercentage(3));
    }

    @Test
    void averagesTheChannelsIntoMono() {
        short[] left = TestAudio.tone(1, 16_000, 500, 8_000);
        short[] right = new short[left.length];

        PcmAudio audio = AudioNormalizer.normalize(TestAudio.wav(TestAudio.interleave(left, right), 16_000, 2));

        assertThat(TestAudio.rms(audio.samples())).isCloseTo(TestAudio.rms(left) / 2, withinPercentage(1));
    }

    @Test
    void suppressesContentAboveTheTargetNyquistInsteadOfAliasingIt() {
        short[] high = TestAudio.tone(1, 44_100, 12_000, 8_000);

        PcmAudio audio = AudioNormalizer.normalize(TestAudio.wav(high, 44_100, 1));

        assertThat(TestAudio.rms(audio.samples())).isLessThan(TestAudio.rms(high) * 0.05);
    }

    @Test
    void keepsA16kMonoRecordingSampleForSample() {
        short[] samples = TestAudio.speech(1.5);

        PcmAudio audio = AudioNormalizer.normalize(TestAudio.wav16kMono(samples));

        assertThat(audio.sampleRate()).isEqualTo(16_000);
        assertThat(audio.samples()).containsExactly(samples);
    }

    @Test
    void readsAnEmptyRecordingAsZeroSamples() {
        PcmAudio audio = AudioNormalizer.normalize(TestAudio.wav16kMono(new short[0]));

        assertThat(audio.samples()).isEmpty();
        assertThat(audio.durationMs()).isZero();
    }

    @Test
    void rejectsBytesThatAreNotAudioAsAPermanentFailure() {
        byte[] notAudio = "definitely not a wav file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AudioNormalizer.normalize(notAudio))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.AUDIO_UNSUPPORTED);
                    assertThat(ex.isTransient()).isFalse();
                });
    }

    @Test
    void rejectsARecordingLongerThanAnyPteAnswerBeforeDecodingIt() {
        // Duration, not file size, bounds the decoded buffers: 8 kHz audio doubles when resampled to 16 kHz.
        byte[] tooLong = TestAudio.wav(new short[181 * 8_000], 8_000, 1);

        assertThatThrownBy(() -> AudioNormalizer.normalize(tooLong))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.AUDIO_TOO_LONG);
                    assertThat(ex.isTransient()).isFalse();
                });
    }

    @Test
    void rejectsMissingAudio() {
        assertThatThrownBy(() -> AudioNormalizer.normalize(new byte[0]))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.AUDIO_UNSUPPORTED));
    }
}
