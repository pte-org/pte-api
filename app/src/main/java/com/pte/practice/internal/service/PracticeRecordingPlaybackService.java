package com.pte.practice.internal.service;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.dto.response.media.PracticeRecordingPlaybackResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hands a student a short-lived link to their own saved recording. Read-only and limited to the student's own
 * data, so it needs no entitlement check and works for any session state (also for a later review page).
 * Everything that is not the caller's own recorded item is reported as not found.
 */
@Service
public class PracticeRecordingPlaybackService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseRepository itemRepository;
    private final PracticeAnswerValidationService answerValidationService;
    private final MediaService mediaService;

    public PracticeRecordingPlaybackService(PracticeSessionRepository sessionRepository,
            PracticeResponseRepository itemRepository, PracticeAnswerValidationService answerValidationService,
            MediaService mediaService) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.answerValidationService = answerValidationService;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public PracticeRecordingPlaybackResponse playback(UUID sessionPublicId, UUID itemPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository
                .findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionPublicId, caller.userId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        if (session.getId() == null || caller.tenantId() == null
                || !caller.tenantId().equals(session.getTenantId())) {
            throw new PracticeSessionNotFoundException();
        }
        PracticeResponse item = itemRepository
                .findByPublicIdAndPracticeSessionIdAndDeletedFalse(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        if (!PracticeConstants.PRACTICE_RECORDING_RENDERER_KEYS.contains(item.getRendererKey())
                || item.getSavedPayload() == null || item.getSavedPayload().isBlank()) {
            throw new PracticeSessionNotFoundException();
        }
        UUID mediaPublicId = recordedMediaId(item);
        PresignedDownloadResponse download;
        try {
            download = mediaService.presignOwnedPracticeResponseAudio(mediaPublicId, sessionPublicId, itemPublicId,
                    caller, PracticeConstants.PRACTICE_RECORDING_PLAYBACK_TTL_SECONDS);
        } catch (RuntimeException ex) {
            // Media reports any ownership/binding/upload mismatch the same way the practice API does.
            throw new PracticeSessionNotFoundException();
        }
        return new PracticeRecordingPlaybackResponse(download.url(), download.expiresInSeconds(),
                download.durationSeconds());
    }

    private UUID recordedMediaId(PracticeResponse item) {
        try {
            return answerValidationService.requireRecordedMediaPublicId(item.getRendererKey(), item.getSavedPayload());
        } catch (PracticeSessionException ex) {
            throw new PracticeSessionNotFoundException();
        }
    }
}
