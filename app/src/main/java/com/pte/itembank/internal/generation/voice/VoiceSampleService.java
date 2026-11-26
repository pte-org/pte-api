package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.media.EnabledVoices;
import com.pte.itembank.internal.generation.media.KokoroSpeechClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A short fixed sentence in each voice, for trying one out. Each voice is spoken once per start of the app and kept
 * in memory (nothing is stored, so there is no new table and no extra file on the shared media account). Callers of
 * the same voice share one speech call; a voice that just failed is not asked again for a minute.
 */
@Service
public class VoiceSampleService {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceSampleService.class);

    private final EnabledVoices enabledVoices;
    private final KokoroSpeechClient speech;
    private final VoiceChangeLimiter limiter;
    private final Clock clock;
    private final Map<String, CompletableFuture<byte[]>> samples = new ConcurrentHashMap<>();
    private final Map<String, Instant> failedUntil = new ConcurrentHashMap<>();

    public VoiceSampleService(EnabledVoices enabledVoices, KokoroSpeechClient speech, VoiceChangeLimiter limiter,
                              Clock clock) {
        this.enabledVoices = enabledVoices;
        this.speech = speech;
        this.limiter = limiter;
        this.clock = clock;
    }

    public byte[] sample(String voice) {
        if (enabledVoices.find(voice).isEmpty()) {
            throw new VoiceChangeException(HttpStatus.BAD_REQUEST, VoiceConstants.GENERATION_VOICE_UNKNOWN);
        }
        Instant retryAfter = failedUntil.get(voice);
        if (retryAfter != null && clock.instant().isBefore(retryAfter)) {
            throw new GenerationMediaException(QuestionGenerationConstants.GENERATION_MEDIA_FAILED);
        }
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> shared = samples.putIfAbsent(voice, mine);
        if (shared != null) {
            return shared.join();
        }
        // The speech call is made here, outside the map's own locking, by the one caller that put its future in.
        try {
            byte[] audio = limiter.run(null, () -> speech.synthesize(VoiceConstants.SAMPLE_TEXT, voice,
                    VoiceConstants.SAMPLE_SPEED));
            mine.complete(audio);
            return audio;
        } catch (RuntimeException failure) {
            samples.remove(voice, mine);
            if (!(failure instanceof VoiceChangeException)) {
                failedUntil.put(voice, clock.instant()
                        .plus(Duration.ofSeconds(VoiceConstants.SAMPLE_FAILURE_CACHE_SECONDS)));
                LOG.warn("A voice sample could not be spoken: voice={}, errorType={}", voice,
                        failure.getClass().getSimpleName());
            }
            mine.completeExceptionally(failure);
            throw failure instanceof VoiceChangeException ? failure
                    : new GenerationMediaException(QuestionGenerationConstants.GENERATION_MEDIA_FAILED);
        }
    }
}
