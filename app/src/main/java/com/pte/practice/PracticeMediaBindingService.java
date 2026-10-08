package com.pte.practice;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Public practice boundary used by media to authorize a student recording.
 * Media never reaches into practice repositories directly; this service keeps
 * session/item ownership and lifecycle rules in the owning module.
 */
@Service
public class PracticeMediaBindingService implements PracticeMediaBindingPort {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final Clock clock;

    public PracticeMediaBindingService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public void assertCanUseResponseAudio(UUID sessionPublicId, UUID itemPublicId, CurrentUser caller) {
        if (sessionPublicId == null || itemPublicId == null || caller == null || caller.userId() == null) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_MEDIA_BINDING_REQUIRED,
                    PracticeConstants.PRACTICE_MEDIA_BINDING_REQUIRED_MESSAGE);
        }
        PracticeSession session = sessionRepository
                .findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionPublicId, caller.userId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        if (caller.tenantId() != null && session.getTenantId() != null
                && !caller.tenantId().equals(session.getTenantId())) {
            throw new PracticeSessionNotFoundException();
        }
        if (session.getId() == null) {
            throw new PracticeSessionNotFoundException();
        }
        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS || isPastDeadline(session)) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_MEDIA_SESSION_NOT_LIVE,
                    PracticeConstants.PRACTICE_MEDIA_SESSION_NOT_LIVE_MESSAGE);
        }
        PracticeSessionItem item = itemRepository
                .findByPublicIdAndPracticeSessionIdAndDeletedFalse(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        if (!PracticeConstants.PRACTICE_RECORDING_RENDERER_KEYS.contains(item.getRendererKey())) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_MEDIA_ITEM_NOT_RECORDABLE,
                    PracticeConstants.PRACTICE_MEDIA_ITEM_NOT_RECORDABLE_MESSAGE);
        }
        if (item.getStatus() != PracticeSessionItemStatus.PENDING) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                    PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
        }
    }

    private boolean isPastDeadline(PracticeSession session) {
        Instant deadline = session.getDeadlineAt();
        return deadline != null && !clock.instant().isBefore(deadline);
    }
}
