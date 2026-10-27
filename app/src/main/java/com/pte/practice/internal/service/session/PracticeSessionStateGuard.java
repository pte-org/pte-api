package com.pte.practice.internal.service.session;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Student-owned session lookup, optimistic version and deadline checks, and the versioned save. */
class PracticeSessionStateGuard {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionResponseMapper responseMapper;
    private final Clock clock;

    PracticeSessionStateGuard(PracticeSessionRepository sessionRepository,
            PracticeSessionResponseMapper responseMapper, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.responseMapper = responseMapper;
        this.clock = clock;
    }

    void requireLiveInProgress(PracticeSession session, long expectedVersion) {
        if (session.getStatus() == PracticeSessionStatus.EXPIRED
                || isPastDeadline(session, clock.instant())) {
            expire(session);
            saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }
        requireVersion(session, expectedVersion);
        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw notStartable();
        }
    }

    PracticeSessionResponse saveAndMap(PracticeSession session) {
        try {
            return responseMapper.toResponse(sessionRepository.saveAndFlush(session));
        } catch (OptimisticLockingFailureException ex) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION_MESSAGE);
        }
    }

    PracticeSession findOwned(UUID publicId, UUID studentPublicId) {
        return sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(publicId, studentPublicId)
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    PracticeSession findOwnedForUpdate(UUID publicId, UUID studentPublicId) {
        return sessionRepository.findWithLockByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    void requireVersion(PracticeSession session, long expectedVersion) {
        long actual = versionOf(session);
        if (actual != expectedVersion) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION_MESSAGE);
        }
    }

    void expireIfNeeded(PracticeSession session) {
        if (session.getStatus() == PracticeSessionStatus.IN_PROGRESS
                && isPastDeadline(session, clock.instant())) {
            expire(session);
            sessionRepository.saveAndFlush(session);
        }
    }

    void expire(PracticeSession session) {
        session.setStatus(PracticeSessionStatus.EXPIRED);
    }

    boolean isPastDeadline(PracticeSession session, Instant now) {
        return session.getDeadlineAt() != null && !now.isBefore(session.getDeadlineAt());
    }

    PracticeSessionException notStartable() {
        return new PracticeSessionException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
    }

    PracticeSessionException invalidAnswerRequest() {
        return new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                PracticeConstants.PRACTICE_ANSWER_INVALID,
                PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
    }

    private long versionOf(PracticeSession session) {
        return session.getVersion() == null ? 0L : session.getVersion();
    }
}
