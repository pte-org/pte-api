package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WavHeadersTest {

    private final AudioDurationProbe probe = new AudioDurationProbe();

    /** A 24 kHz mono 16-bit WAV in the shape Kokoro streams it: unknown sizes and a LIST chunk before the data. */
    private static byte[] streamedWav(int samples) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44 + 8 + 26).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(-1).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(24000).putInt(48000).putShort((short) 2).putShort((short) 16);
        header.put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(26)
                .put("INFO".getBytes(StandardCharsets.US_ASCII)).put(new byte[22]);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(-1);
        out.writeBytes(header.array());
        out.writeBytes(new byte[samples * 2]);
        return out.toByteArray();
    }

    @Test
    void aStreamedWavGetsItsRealSizesSoItsLengthCanBeRead() {
        byte[] streamed = streamedWav(48000); // two seconds

        byte[] fixed = WavHeaders.normalize(streamed);

        assertThat(probe.durationSeconds(fixed)).isEqualTo(2.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN).getInt(4)).isEqualTo(fixed.length - 8);
        assertThat(fixed).hasSameSizeAs(streamed);
    }

    @Test
    void aWavWithCorrectSizesIsUnchanged() {
        byte[] once = WavHeaders.normalize(streamedWav(24000));

        assertThat(WavHeaders.normalize(once)).isEqualTo(once);
    }

    @Test
    void somethingThatIsNotAWavIsReturnedAsIs() {
        byte[] notWav = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};

        assertThat(WavHeaders.normalize(notWav)).isSameAs(notWav);
        assertThat(WavHeaders.normalize(null)).isNull();
    }
}
