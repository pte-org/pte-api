package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.domain.Question;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.generation.media.SpeechFitter;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saves a question's new audio. Database work only: the speech that made the audio ran before, outside any
 * transaction, so no connection was held while the speech server worked.
 */
@Service
public class QuestionVoiceWriter {

    private final QuestionRepository questionRepository;
    private final DraftWriteGuard guard;
    private final QuestionMediaReferenceValidator mediaValidator;
    private final AuditLogService auditLogService;

    public QuestionVoiceWriter(QuestionRepository questionRepository, DraftWriteGuard guard,
                               QuestionMediaReferenceValidator mediaValidator, AuditLogService auditLogService) {
        this.questionRepository = questionRepository;
        this.guard = guard;
        this.mediaValidator = mediaValidator;
        this.auditLogService = auditLogService;
    }

    /** The old audio file is kept: a published exam still points at it. */
    @Transactional
    public QuestionResponse apply(UUID publicId, long expectedVersion, String voice, UUID newAudioRef,
                                  SpeechFitter.FittedSpeech speech, CurrentUser caller) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        // Asked again on the question as it is now: it may have been edited, approved or taken over meanwhile.
        guard.require(question, caller, expectedVersion);
        String oldVoice = GenerationMediaMeta.currentVoice(question.getGenerationMeta()).orElse("");
        UUID oldAudioRef = question.getAudioPromptRef();
        question.setAudioPromptRef(newAudioRef);
        question.setGenerationMeta(
                GenerationMediaMeta.withVoice(question.getGenerationMeta(), newAudioRef, voice, speech));
        mediaValidator.validate(question, caller);
        auditLogService.record(caller, ItembankConstants.QUESTION_AGGREGATE, publicId.toString(),
                ItembankConstants.AUDIT_AUDIO_VOICE_CHANGED,
                String.format(ItembankConstants.AUDIT_SUMMARY_AUDIO_VOICE_CHANGED, oldVoice, voice, oldAudioRef,
                        newAudioRef));
        return QuestionMapper.toResponse(question);
    }
}
