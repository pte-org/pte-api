package com.pte.scoring.internal.vendor.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Canonical 44-byte-header WAV for mono 16-bit PCM. */
final class WavEncoder {

    private static final int HEADER_BYTES = 44;
    private static final int BYTES_PER_SAMPLE = 2;

    private WavEncoder() {
    }

    static byte[] encode(short[] samples, int fromIndex, int toIndex, int sampleRate) {
        int dataLength = (toIndex - fromIndex) * BYTES_PER_SAMPLE;
        ByteBuffer wav = ByteBuffer.allocate(HEADER_BYTES + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII))
                .putInt(HEADER_BYTES - 8 + dataLength)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16)
                .putShort((short) 1)
                .putShort((short) 1)
                .putInt(sampleRate)
                .putInt(sampleRate * BYTES_PER_SAMPLE)
                .putShort((short) BYTES_PER_SAMPLE)
                .putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII))
                .putInt(dataLength);
        wav.asShortBuffer().put(samples, fromIndex, toIndex - fromIndex);
        return wav.array();
    }
}
