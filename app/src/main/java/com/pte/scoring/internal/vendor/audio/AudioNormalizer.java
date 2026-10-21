package com.pte.scoring.internal.vendor.audio;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Brings a WAV recording to what Azure short-audio expects: 16 kHz mono
 * 16-bit PCM. Uses only the JDK, so it behaves the same on the amd64 dev box
 * and the arm64 VPS. Anything the JDK cannot read (m4a, mp3, corrupt bytes)
 * is a permanent {@link AiFailureReason#AUDIO_UNSUPPORTED} failure.
 */
public final class AudioNormalizer {

    private static final int TARGET_SAMPLE_RATE = 16_000;
    // Well above the longest PTE answer (2 min); bounds the decoded buffers on the 512 MB VPS.
    private static final int MAX_DURATION_SECONDS = 180;
    private static final int READ_BLOCK_BYTES = 64 * 1024;

    // Resampling kernel: windowed sinc, cut a little below the new Nyquist so
    // content the target rate cannot represent is filtered, not aliased.
    private static final double LOWPASS_FRACTION = 0.95;
    private static final int SINC_ZERO_CROSSINGS = 16;
    private static final int KERNEL_STEPS_PER_SAMPLE = 512;

    private AudioNormalizer() {
    }

    public static PcmAudio normalize(byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw unsupported();
        }
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
            AudioFormat format = source.getFormat();
            int channels = format.getChannels();
            int sampleRate = Math.round(format.getSampleRate());
            if (channels < 1 || sampleRate < 1) {
                throw unsupported();
            }
            AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels,
                    channels * 2, sampleRate, false);
            // Every frame takes frame-size bytes of the file, which caps a header that overstates its length.
            long maxFrames = audio.length / Math.max(1, format.getFrameSize());
            if (source.getFrameLength() >= 0) {
                maxFrames = Math.min(maxFrames, source.getFrameLength());
            }
            if (maxFrames > (long) sampleRate * MAX_DURATION_SECONDS) {
                throw new AiProviderException(AiFailureReason.AUDIO_TOO_LONG,
                        ScoringConstants.AUDIO_TOO_LONG.formatted(MAX_DURATION_SECONDS));
            }
            float[] mono;
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm16, source)) {
                mono = readMono(converted, channels, (int) maxFrames);
            }
            return new PcmAudio(clip(resample(mono, sampleRate, TARGET_SAMPLE_RATE)), TARGET_SAMPLE_RATE);
        } catch (UnsupportedAudioFileException | IOException | IllegalArgumentException ex) {
            throw unsupported();
        }
    }

    /**
     * Reads 16-bit little-endian PCM a block at a time, averaging the channels
     * as it goes, so the decoded recording is never held as bytes or shorts.
     */
    private static float[] readMono(AudioInputStream pcm, int channels, int maxFrames) throws IOException {
        int frameBytes = channels * 2;
        byte[] block = new byte[Math.max(frameBytes, READ_BLOCK_BYTES - READ_BLOCK_BYTES % frameBytes)];
        ByteBuffer samples = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        float[] mono = new float[maxFrames];
        int frames = 0;
        int read;
        while (frames < maxFrames && (read = pcm.readNBytes(block, 0, block.length)) > 0) {
            int blockFrames = Math.min(read / frameBytes, maxFrames - frames);
            for (int frame = 0; frame < blockFrames; frame++) {
                float sum = 0;
                for (int channel = 0; channel < channels; channel++) {
                    sum += samples.getShort((frame * channels + channel) * 2);
                }
                mono[frames++] = sum / channels;
            }
        }
        return frames == maxFrames ? mono : Arrays.copyOf(mono, frames);
    }

    private static float[] resample(float[] input, int inputRate, int outputRate) {
        if (inputRate == outputRate) {
            return input;
        }
        double step = (double) inputRate / outputRate;
        double cutoff = Math.min(1.0, (double) outputRate / inputRate) * LOWPASS_FRACTION;
        int halfWidth = (int) Math.ceil(SINC_ZERO_CROSSINGS / cutoff);
        double[] kernel = kernelTable(cutoff, halfWidth);

        float[] output = new float[(int) ((long) input.length * outputRate / inputRate)];
        for (int n = 0; n < output.length; n++) {
            double center = n * step;
            int nearest = (int) Math.floor(center);
            int first = Math.max(0, nearest - halfWidth + 1);
            int last = Math.min(input.length - 1, nearest + halfWidth);
            double sum = 0;
            double weightSum = 0;
            for (int k = first; k <= last; k++) {
                double weight = kernelAt(kernel, Math.abs(center - k));
                sum += input[k] * weight;
                weightSum += weight;
            }
            // Dividing by the weight sum keeps unity gain, including at the edges.
            output[n] = weightSum == 0 ? 0f : (float) (sum / weightSum);
        }
        return output;
    }

    /** Blackman-windowed sinc sampled densely on [0, halfWidth], read back with linear interpolation. */
    private static double[] kernelTable(double cutoff, int halfWidth) {
        double[] table = new double[halfWidth * KERNEL_STEPS_PER_SAMPLE + 2];
        for (int i = 0; i < table.length; i++) {
            double x = (double) i / KERNEL_STEPS_PER_SAMPLE;
            double t = x / halfWidth;
            double window = t >= 1 ? 0 : 0.42 + 0.5 * Math.cos(Math.PI * t) + 0.08 * Math.cos(2 * Math.PI * t);
            table[i] = cutoff * sinc(cutoff * x) * window;
        }
        return table;
    }

    private static double kernelAt(double[] table, double distance) {
        double position = distance * KERNEL_STEPS_PER_SAMPLE;
        int index = (int) position;
        if (index >= table.length - 1) {
            return 0;
        }
        double fraction = position - index;
        return table[index] + fraction * (table[index + 1] - table[index]);
    }

    private static double sinc(double x) {
        return x == 0 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
    }

    private static short[] clip(float[] samples) {
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(samples[i])));
        }
        return out;
    }

    private static AiProviderException unsupported() {
        return new AiProviderException(AiFailureReason.AUDIO_UNSUPPORTED, ScoringConstants.AUDIO_UNSUPPORTED);
    }
}
