package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialogueSynthesizerTest {

    private final DialogueSynthesizer synthesizer = new DialogueSynthesizer();
    private final AudioDurationProbe probe = new AudioDurationProbe();

    private static byte[] clip(float sampleRate, int channels, double seconds) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        long frames = (long) (sampleRate * seconds);
        byte[] pcm = new byte[(int) (frames * format.getFrameSize())];
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), format, frames),
                AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    @Test
    void joinsTheClipsWithASilenceBetweenThemUnderOneHeader() throws Exception {
        byte[] joined = synthesizer.synthesize(List.of(
                new DialogueSynthesizer.DialogueTurn("A", clip(24000, 1, 1.0)),
                new DialogueSynthesizer.DialogueTurn("B", clip(24000, 1, 2.0)),
                new DialogueSynthesizer.DialogueTurn("A", clip(24000, 1, 1.0))));

        // 1 + 2 + 1 seconds of speech and two 0.3 second pauses
        assertThat(probe.durationSeconds(joined)).isEqualTo(4.6, org.assertj.core.data.Offset.offset(0.01));
        // A single RIFF header: no second "RIFF" tag inside the audio
        String text = new String(joined, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(text.indexOf("RIFF", 4)).isNegative();
    }

    @Test
    void aSingleClipIsReturnedAsOneWavWithoutSilence() throws Exception {
        byte[] joined = synthesizer.synthesize(List.of(new DialogueSynthesizer.DialogueTurn("A", clip(24000, 1, 1.5))));

        assertThat(probe.durationSeconds(joined)).isEqualTo(1.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void clipsWithDifferentSampleRatesCannotBeJoined() throws Exception {
        List<DialogueSynthesizer.DialogueTurn> turns = List.of(
                new DialogueSynthesizer.DialogueTurn("A", clip(24000, 1, 0.5)),
                new DialogueSynthesizer.DialogueTurn("B", clip(16000, 1, 0.5)));

        assertThatThrownBy(() -> synthesizer.synthesize(turns)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nothingToJoinOrUnreadableAudioIsRejected() {
        assertThatThrownBy(() -> synthesizer.synthesize(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> synthesizer.synthesize(List.of(
                new DialogueSynthesizer.DialogueTurn("A", new byte[] {1, 2, 3}))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
