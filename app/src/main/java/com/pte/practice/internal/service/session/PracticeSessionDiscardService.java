package com.pte.practice.internal.service.session;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "Thoát không lưu": a session with nothing answered or drafted has nothing to resume, so leaving it this way drops
 * it instead of listing it as in progress. A session with saved answers stays resumable; "Lưu" keeps using
 * save-and-exit, which never discards a begun session. Repeating the call is harmless.
 */
@Service
public class PracticeSessionDiscardService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeResponseService itemService;
    private final Clock clock;

    public PracticeSessionDiscardService(PracticeSessionRepository sessionRepository,
            PracticeResponseService itemService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.itemService = itemService;
        this.clock = clock;
    }

    /** True when the session was dropped; false when it keeps saved answers (or is already finished). */
    @Transactional
    public boolean exitWithoutSaving(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository.findWithLockByPublicIdAndStudentPublicId(sessionPublicId, caller.userId())
                .orElseThrow(PracticeSessionNotFoundException::new);
        boolean open = session.getStatus() == PracticeSessionStatus.OVERVIEW
                || session.getStatus() == PracticeSessionStatus.IN_PROGRESS;
        if (!open || itemService.hasProgress(session)) {
            return false;
        }
        Instant now = clock.instant();
        session.setStatus(PracticeSessionStatus.DISCARDED);
        session.setDiscardedAt(now);
        session.setLastActivityAt(now);
        sessionRepository.saveAndFlush(session);
        return true;
    }
}
