package com.pte.scoring.internal.vendor.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SilenceChunkerTest {

    @Test
    void keepsAudioUpToTheLimitAsOneChunk() {
        PcmAudio audio = TestAudio.pcm(TestAudio.speech(28));

        List<AudioChunk> chunks = SilenceChunker.split(audio);

        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 28_000L));
        assertValidWav(chunks.getFirst());
    }

    @Test
    void cutsInTheMiddleOfAPauseInsideTheWindow() {
        PcmAudio audio = TestAudio.pcm(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(17.7));

        List<AudioChunk> chunks = SilenceChunker.split(audio);

        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 22_150L), tuple(22_150L, 17_850L));
    }

    @Test
    void prefersTheLongestPauseInsideTheWindow() {
        PcmAudio audio = TestAudio.pcm(TestAudio.speech(19), TestAudio.silence(0.24), TestAudio.speech(5.76),
                TestAudio.silence(0.4), TestAudio.speech(14.6));

        List<AudioChunk> chunks = SilenceChunker.split(audio);

        assertThat(chunks.getFirst().durationMs()).isEqualTo(25_200L);
    }

    @Test
    void cutsAtTheQuietestFrameWhenThereIsNoPause() {
        short[] samples = TestAudio.speech(40);
        // Frame 1200 (24.00-24.02 s) is the quietest but far too short to count as a pause.
        for (int i = 1200 * 320; i < 1201 * 320; i++) {
            samples[i] = (short) (samples[i] / 8);
        }

        List<AudioChunk> chunks = SilenceChunker.split(TestAudio.pcm(samples));

        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 24_010L), tuple(24_010L, 15_990L));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.durationMs()).isLessThanOrEqualTo(28_000L));
    }

    @Test
    void splitsALongRecordingIntoContiguousValidChunksWithinTheLimit() {
        List<short[]> parts = new ArrayList<>();
        double total = 0;
        while (total < 120) {
            double speech = Math.min(5, 120 - total);
            parts.add(TestAudio.speech(speech));
            total += speech;
            if (total < 120) {
                parts.add(TestAudio.silence(0.5));
                total += 0.5;
            }
        }

        List<AudioChunk> chunks = SilenceChunker.split(TestAudio.pcm(parts.toArray(short[][]::new)));

        assertThat(chunks).hasSizeGreaterThan(4);
        long expectedOffset = 0;
        for (AudioChunk chunk : chunks) {
            assertThat(chunk.offsetMs()).isEqualTo(expectedOffset);
            assertThat(chunk.durationMs()).isPositive().isLessThanOrEqualTo(28_000L);
            assertValidWav(chunk);
            expectedOffset += chunk.durationMs();
        }
        assertThat(chunks.subList(0, chunks.size() - 1))
                .allSatisfy(chunk -> assertThat(chunk.durationMs()).isGreaterThanOrEqualTo(18_000L));
        assertThat(expectedOffset).isEqualTo(120_000L);
    }

    @Test
    void dropsATrailingChunkThatIsOnlySilence() {
        PcmAudio audio = TestAudio.pcm(TestAudio.speech(25), TestAudio.silence(15));

        List<AudioChunk> chunks = SilenceChunker.split(audio);

        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 26_500L));
    }

    @Test
    void treatsDigitalSilenceAsSilenceEvenWhenTheWholeRecordingIsSilent() {
        List<AudioChunk> chunks = SilenceChunker.split(TestAudio.pcm(TestAudio.silence(40)));

        // Cut in the middle of the 18–28 s window; the silent rest is dropped.
        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 23_000L));
    }

    @Test
    void writesAHeaderThatMatchesEachChunksData() throws Exception {
        PcmAudio audio = TestAudio.pcm(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(17.7));

        for (AudioChunk chunk : SilenceChunker.split(audio)) {
            assertValidWav(chunk);
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(chunk.wav()))) {
                assertThat(stream.getFormat().getSampleRate()).isEqualTo(16_000f);
                assertThat(stream.getFormat().getChannels()).isEqualTo(1);
                assertThat(stream.getFormat().getSampleSizeInBits()).isEqualTo(16);
                assertThat(stream.getFrameLength()).isEqualTo(chunk.durationMs() * 16);
            }
        }
    }

    @Test
    void returnsOneEmptyChunkForAnEmptyRecording() {
        List<AudioChunk> chunks = SilenceChunker.split(TestAudio.pcm(new short[0]));

        assertThat(chunks).extracting(AudioChunk::offsetMs, AudioChunk::durationMs)
                .containsExactly(tuple(0L, 0L));
    }

    private static void assertValidWav(AudioChunk chunk) {
        byte[] wav = chunk.wav();
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int dataLength = (int) (chunk.durationMs() * 16 * 2);
        assertThat(ascii(wav, 0)).isEqualTo("RIFF");
        assertThat(header.getInt(4)).isEqualTo(wav.length - 8);
        assertThat(ascii(wav, 8)).isEqualTo("WAVE");
        assertThat(ascii(wav, 12)).isEqualTo("fmt ");
        assertThat(header.getInt(16)).isEqualTo(16);
        assertThat(header.getShort(20)).isEqualTo((short) 1);
        assertThat(header.getShort(22)).isEqualTo((short) 1);
        assertThat(header.getInt(24)).isEqualTo(16_000);
        assertThat(header.getInt(28)).isEqualTo(32_000);
        assertThat(header.getShort(32)).isEqualTo((short) 2);
        assertThat(header.getShort(34)).isEqualTo((short) 16);
        assertThat(ascii(wav, 36)).isEqualTo("data");
        assertThat(header.getInt(40)).isEqualTo(dataLength);
        assertThat(wav.length).isEqualTo(44 + dataLength);
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
