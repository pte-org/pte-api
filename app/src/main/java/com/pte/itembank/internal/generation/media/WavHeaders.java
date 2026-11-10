package com.pte.itembank.internal.generation.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Kokoro streams its WAV: the RIFF and data sizes in the header are 0xFFFFFFFF because the length is not known when
 * the header is written. Players, Cloudinary and audio libraries cannot rely on that, so the sizes are rewritten to
 * the real lengths. A WAV whose header is already correct is returned unchanged.
 */
public final class WavHeaders {

    private static final int RIFF_HEADER = 12;
    private static final int CHUNK_HEADER = 8;

    private WavHeaders() {
    }

    /** The WAV with its RIFF size and data chunk size set to the bytes actually present. */
    public static byte[] normalize(byte[] wav) {
        if (wav == null || wav.length < RIFF_HEADER + CHUNK_HEADER || !tag(wav, 0, "RIFF") || !tag(wav, 8, "WAVE")) {
            return wav;
        }
        byte[] fixed = wav.clone();
        ByteBuffer buffer = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(4, fixed.length - CHUNK_HEADER);
        int position = RIFF_HEADER;
        while (position + CHUNK_HEADER <= fixed.length) {
            long declared = Integer.toUnsignedLong(buffer.getInt(position + 4));
            if (tag(fixed, position, "data")) {
                buffer.putInt(position + 4, fixed.length - position - CHUNK_HEADER);
                return fixed;
            }
            long next = position + CHUNK_HEADER + declared + (declared % 2);
            if (next > fixed.length) {
                return wav;
            }
            position = (int) next;
        }
        return wav;
    }

    private static boolean tag(byte[] bytes, int offset, String tag) {
        for (int i = 0; i < tag.length(); i++) {
            if (bytes[offset + i] != tag.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
