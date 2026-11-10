package com.pte.itembank.internal.generation.media;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Extracts duration (in seconds) from WAV file header.
 * Reads RIFF header structure to calculate duration from sample rate and byte count.
 */
@Component
public class AudioDurationProbe {

    private static final int RIFF_CHUNK_SIZE_OFFSET = 4;
    private static final int FORMAT_SUBCHUNK1_SIZE_OFFSET = 16;
    private static final int AUDIO_FORMAT_OFFSET = 20;
    private static final int NUM_CHANNELS_OFFSET = 22;
    private static final int SAMPLE_RATE_OFFSET = 24;
    private static final int BYTE_RATE_OFFSET = 28;
    private static final int BLOCK_ALIGN_OFFSET = 32;
    private static final int BITS_PER_SAMPLE_OFFSET = 34;
    private static final int MIN_WAV_HEADER_SIZE = 44;

    /**
     * Calculate audio duration in seconds from WAV bytes.
     *
     * @param wavBytes complete WAV file bytes
     * @return duration in seconds (fractional), or -1 if unable to parse
     */
    public double durationSeconds(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length < MIN_WAV_HEADER_SIZE) {
            return -1;
        }

        try {
            // Check RIFF signature
            if (wavBytes[0] != 'R' || wavBytes[1] != 'I'
                    || wavBytes[2] != 'F' || wavBytes[3] != 'F') {
                return -1;
            }

            // Check WAVE signature
            if (wavBytes[8] != 'W' || wavBytes[9] != 'A'
                    || wavBytes[10] != 'V' || wavBytes[11] != 'E') {
                return -1;
            }

            // Find 'data' chunk to calculate duration
            ByteBuffer buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN);

            // Read sample rate from fmt subchunk
            int sampleRate = buffer.getInt(SAMPLE_RATE_OFFSET);
            if (sampleRate <= 0) {
                return -1;
            }

            // Find data chunk
            int dataSize = findDataChunkSize(wavBytes);
            if (dataSize <= 0) {
                return -1;
            }

            // Duration = bytes / (sample rate * channels * bytes per sample)
            int channels = buffer.getShort(NUM_CHANNELS_OFFSET) & 0xFFFF;
            int bitsPerSample = buffer.getShort(BITS_PER_SAMPLE_OFFSET) & 0xFFFF;
            int bytesPerSample = bitsPerSample / 8;
            int bytesPerSecond = sampleRate * channels * bytesPerSample;

            if (bytesPerSecond <= 0) {
                return -1;
            }

            return (double) dataSize / bytesPerSecond;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Find the size of the 'data' chunk in WAV file.
     */
    private int findDataChunkSize(byte[] wavBytes) {
        // Look for 'data' chunk starting after fmt subchunk
        for (int i = 36; i < wavBytes.length - 8; i++) {
            if (wavBytes[i] == 'd' && wavBytes[i + 1] == 'a'
                    && wavBytes[i + 2] == 't' && wavBytes[i + 3] == 'a') {
                ByteBuffer buffer = ByteBuffer.wrap(wavBytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN);
                return buffer.getInt();
            }
        }
        return -1;
    }
}
