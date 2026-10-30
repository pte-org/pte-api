package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

/** Audited authorization denials and the practice-binding proof for student response audio. */
class MediaAccessGuard {

    private final PracticeMediaBindingPort practiceMediaBindingService;
    private final AuditLogService auditLogService;

    MediaAccessGuard(PracticeMediaBindingPort practiceMediaBindingService, AuditLogService auditLogService) {
        this.practiceMediaBindingService = practiceMediaBindingService;
        this.auditLogService = auditLogService;
    }

    void requirePracticeBinding(MediaAsset media, CurrentUser caller) {
        if (practiceMediaBindingService == null
                || media.getPracticeSessionPublicId() == null
                || media.getPracticeItemPublicId() == null
                || !PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(media.getPurpose())) {
            throw new MediaNotFoundException();
        }
        practiceMediaBindingService.assertCanUseResponseAudio(
                media.getPracticeSessionPublicId(), media.getPracticeItemPublicId(), caller);
    }

    static boolean isPracticeResponseAudio(MediaAsset media) {
        return PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(media.getPurpose());
    }

    void deny(CurrentUser caller, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, "MEDIA", "authoring",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }
}
