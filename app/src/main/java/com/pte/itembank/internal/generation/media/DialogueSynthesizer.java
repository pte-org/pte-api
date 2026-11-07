package com.pte.itembank.internal.generation.media;

import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Joins the per-speaker WAV clips of a dialogue into one WAV: the PCM of each clip, a short silence between clips,
 * and a single new header. (Appending whole files would leave a header in the middle of the audio.)
 */
@Component
public class DialogueSynthesizer {

    /** Pause between two turns. */
    static final int SILENCE_MILLIS = 300;

    /** One spoken turn: who speaks and the WAV of what they say. */
    public record DialogueTurn(String speaker, byte[] audioBytes) {
    }

    /**
     * @throws IllegalArgumentException when there is nothing to join, a clip is not readable WAV, or the clips do not
     *                                  share one sample rate, channel count and sample size
     */
    public byte[] synthesize(List<DialogueTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("No dialogue turns provided");
        }
        AudioFormat format = null;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (int i = 0; i < turns.size(); i++) {
            try (AudioInputStream clip = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(turns.get(i).audioBytes()))) {
                AudioFormat clipFormat = clip.getFormat();
                if (format == null) {
                    format = clipFormat;
                } else if (!sameFormat(format, clipFormat)) {
                    throw new IllegalArgumentException("Dialogue clips use different audio formats");
                }
                if (i > 0) {
                    pcm.write(silence(format));
                }
                pcm.write(clip.readAllBytes());
            } catch (UnsupportedAudioFileException | IOException unreadable) {
                throw new IllegalArgumentException("A dialogue clip is not readable WAV audio");
            }
        }
        return toWav(pcm.toByteArray(), format);
    }

    private static boolean sameFormat(AudioFormat first, AudioFormat second) {
        return first.getSampleRate() == second.getSampleRate()
                && first.getChannels() == second.getChannels()
                && first.getSampleSizeInBits() == second.getSampleSizeInBits();
    }

    private static byte[] silence(AudioFormat format) {
        int frames = (int) (format.getSampleRate() * SILENCE_MILLIS / 1000);
        return new byte[frames * format.getFrameSize()];
    }

    private static byte[] toWav(byte[] pcm, AudioFormat format) {
        long frames = pcm.length / format.getFrameSize();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            ByteArrayOutputStream wav = new ByteArrayOutputStream();
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav);
            return wav.toByteArray();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Could not write the dialogue WAV");
        }
    }
}
