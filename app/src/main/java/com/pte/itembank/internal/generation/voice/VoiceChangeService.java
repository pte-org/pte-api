package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.domain.Question;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.media.EnabledVoices;
import com.pte.itembank.internal.generation.media.SpeechFitter;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Speaks a saved draft question again with another voice. It holds no transaction on purpose: speaking can take
 * a minute and a half, and the question is saved by {@link QuestionVoiceWriter} afterwards, which checks it again.
 */
@Service
public class VoiceChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceChangeService.class);

    private final QuestionRepository questionRepository;
    private final EnabledVoices enabledVoices;
    private final DraftWriteGuard guard;
    private final SpokenTextResolver resolver;
    private final SpeechFitter fitter;
    private final VoiceChangeLimiter limiter;
    private final MediaService media;
    private final QuestionVoiceWriter writer;

    public VoiceChangeService(QuestionRepository questionRepository, EnabledVoices enabledVoices,
                              DraftWriteGuard guard, SpokenTextResolver resolver, SpeechFitter fitter,
                              VoiceChangeLimiter limiter, MediaService media, QuestionVoiceWriter writer) {
        this.questionRepository = questionRepository;
        this.enabledVoices = enabledVoices;
        this.guard = guard;
        this.resolver = resolver;
        this.fitter = fitter;
        this.limiter = limiter;
        this.media = media;
        this.writer = writer;
    }

    public QuestionResponse changeVoice(UUID publicId, String voice, long version, CurrentUser caller) {
        if (enabledVoices.find(voice).isEmpty()) {
            throw new VoiceChangeException(HttpStatus.BAD_REQUEST, VoiceConstants.GENERATION_VOICE_UNKNOWN);
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        guard.require(question, caller, version);
        String text = resolver.resolve(question).orElseThrow(() -> new VoiceChangeException(
                HttpStatus.UNPROCESSABLE_ENTITY, VoiceConstants.GENERATION_VOICE_NOT_SUPPORTED_FOR_TASK));
        if (voice.equals(resolver.currentVoice(question).orElse(null))) {
            throw new VoiceChangeException(HttpStatus.CONFLICT, VoiceConstants.GENERATION_VOICE_UNCHANGED);
        }
        String taskTypeKey = question.getPteTaskType().name();
        SpeechFitter.FittedSpeech speech = limiter.run(caller.userId(), () -> speak(text, voice, taskTypeKey));
        UUID audioRef = media.storeGenerated(speech.audio(), VoiceConstants.AUDIO_WAV, VoiceConstants.AUDIO_PROMPT,
                question.getPool() == null ? null : question.getPool().name(), caller);
        try {
            return writer.apply(publicId, version, voice, audioRef, speech, caller);
        } catch (RuntimeException failure) {
            discardNewAudio(audioRef, caller);
            throw failure;
        }
    }

    private SpeechFitter.FittedSpeech speak(String text, String voice, String taskTypeKey) {
        try {
            return fitter.fit(text, voice, taskTypeKey);
        } catch (GenerationMediaException lengthOrMediaError) {
            throw lengthOrMediaError;
        } catch (RuntimeException speechFailure) {
            // Only the failure class is logged: the text and the speech server's answer stay out of logs and replies.
            LOG.warn("Changing a voice failed while speaking: errorType={}", speechFailure.getClass().getSimpleName());
            throw new GenerationMediaException(QuestionGenerationConstants.GENERATION_MEDIA_FAILED);
        }
    }

    /** Only the file this call made: the question's earlier audio stays, a published exam may use it. */
    private void discardNewAudio(UUID audioRef, CurrentUser caller) {
        try {
            media.deleteGenerated(audioRef, caller);
        } catch (RuntimeException cleanupFailure) {
            LOG.warn("A new audio file could not be removed and is left unused: audioRef={}, errorType={}", audioRef,
                    cleanupFailure.getClass().getSimpleName());
        }
    }
}
