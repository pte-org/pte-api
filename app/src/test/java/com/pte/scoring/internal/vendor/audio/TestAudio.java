package com.pte.scoring.internal.vendor.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Synthetic recordings for audio tests: deterministic tones and digital silence. */
public final class TestAudio {

    public static final int RATE = 16_000;

    private TestAudio() {
    }

    public static short[] tone(double seconds, int rate, double hz, int amplitude) {
        int length = (int) Math.round(seconds * rate);
        short[] samples = new short[length];
        for (int i = 0; i < length; i++) {
            samples[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * hz * i / rate));
        }
        return samples;
    }

    /**
     * Stand-in for speech: a 500 Hz tone fits exactly ten periods in a 20 ms
     * frame at 16 kHz, so every full frame has the same RMS.
     */
    public static short[] speech(double seconds) {
        return tone(seconds, RATE, 500, 8_000);
    }

    public static short[] silence(double seconds) {
        return new short[(int) Math.round(seconds * RATE)];
    }

    public static short[] concat(short[]... parts) {
        int length = 0;
        for (short[] part : parts) {
            length += part.length;
        }
        short[] joined = new short[length];
        int position = 0;
        for (short[] part : parts) {
            System.arraycopy(part, 0, joined, position, part.length);
            position += part.length;
        }
        return joined;
    }

    public static PcmAudio pcm(short[]... parts) {
        return new PcmAudio(concat(parts), RATE);
    }

    public static byte[] wav16kMono(short[]... parts) {
        return wav(concat(parts), RATE, 1);
    }

    public static byte[] wav(short[] interleaved, int rate, int channels) {
        ByteBuffer pcm = ByteBuffer.allocate(interleaved.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : interleaved) {
            pcm.putShort(sample);
        }
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm.array()), format, interleaved.length / channels)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static short[] interleave(short[] left, short[] right) {
        short[] stereo = new short[left.length * 2];
        for (int i = 0; i < left.length; i++) {
            stereo[2 * i] = left[i];
            stereo[2 * i + 1] = right[i];
        }
        return stereo;
    }

    public static double rms(short[] samples) {
        double sum = 0;
        for (short sample : samples) {
            sum += (double) sample * sample;
        }
        return samples.length == 0 ? 0 : Math.sqrt(sum / samples.length);
    }
}
