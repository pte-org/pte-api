package com.pte.itembank.internal.generation.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AudioDurationProbe.
 */
class AudioDurationProbeTest {

    private final AudioDurationProbe probe = new AudioDurationProbe();

    @Test
    void testDurationSecondsValidWav() throws Exception {
        byte[] wavBytes = createMinimalWav(16000, 1, 16, 1); // 1 second at 16kHz
        double duration = probe.durationSeconds(wavBytes);
        assertEquals(1.0, duration, 0.001);
    }

    @Test
    void testDurationSecondsInvalidHeader() {
        byte[] invalidBytes = new byte[]{1, 2, 3, 4, 5};
        double duration = probe.durationSeconds(invalidBytes);
        assertEquals(-1.0, duration, 0.0);
    }

    @Test
    void testDurationSecondsNull() {
        double duration = probe.durationSeconds(null);
        assertEquals(-1.0, duration, 0.0);
    }

    @Test
    void testDurationSecondsShortArray() {
        byte[] shortBytes = new byte[10];
        double duration = probe.durationSeconds(shortBytes);
        assertEquals(-1.0, duration, 0.0);
    }

    /**
     * Creates a minimal WAV file header for testing.
     */
    private byte[] createMinimalWav(int sampleRate, int channels, int bitsPerSample, int durationSeconds)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        int bytesPerSample = bitsPerSample / 8;
        int byteRate = sampleRate * channels * bytesPerSample;
        int blockAlign = channels * bytesPerSample;
        int dataSize = sampleRate * durationSeconds * channels * bytesPerSample;
        int fileSize = 36 + dataSize;

        // RIFF header
        dos.writeByte('R');
        dos.writeByte('I');
        dos.writeByte('F');
        dos.writeByte('F');
        dos.writeInt(Integer.reverseBytes(fileSize));

        // WAVE header
        dos.writeByte('W');
        dos.writeByte('A');
        dos.writeByte('V');
        dos.writeByte('E');

        // fmt subchunk
        dos.writeByte('f');
        dos.writeByte('m');
        dos.writeByte('t');
        dos.writeByte(' ');
        dos.writeInt(Integer.reverseBytes(16)); // subchunk1 size
        dos.writeShort(Short.reverseBytes((short) 1)); // audio format (PCM)
        dos.writeShort(Short.reverseBytes((short) channels));
        dos.writeInt(Integer.reverseBytes(sampleRate));
        dos.writeInt(Integer.reverseBytes(byteRate));
        dos.writeShort(Short.reverseBytes((short) blockAlign));
        dos.writeShort(Short.reverseBytes((short) bitsPerSample));

        // data subchunk
        dos.writeByte('d');
        dos.writeByte('a');
        dos.writeByte('t');
        dos.writeByte('a');
        dos.writeInt(Integer.reverseBytes(dataSize));

        // Dummy audio data
        for (int i = 0; i < dataSize; i++) {
            dos.writeByte(0);
        }

        dos.close();
        return baos.toByteArray();
    }
}
