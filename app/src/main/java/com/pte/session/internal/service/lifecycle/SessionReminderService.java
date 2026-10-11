package com.pte.session.internal.service.lifecycle;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Closing-soon candidates and locked revalidation for notification callers. */
@Service
public class SessionReminderService {

    private final ExamSessionRepository sessionRepository;

    public SessionReminderService(ExamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<ClosingSoonSessionView> findDueClosingSoonSessions(Instant now, Instant cutoff) {
        if (now == null || cutoff == null || !cutoff.isAfter(now)) {
            return List.of();
        }
        return sessionRepository.findDueClosingSoon(now, cutoff).stream()
                .map(SessionReminderService::toClosingSoonView)
                .toList();
    }

    @Transactional
    public Optional<ClosingSoonSessionView> lockDueClosingSoonSession(UUID publicId, Instant now, Instant cutoff) {
        if (publicId == null || now == null || cutoff == null || !cutoff.isAfter(now)) {
            return Optional.empty();
        }
        return sessionRepository.findWithLockByPublicId(publicId)
                .filter(session -> !session.isDeleted())
                .filter(session -> session.getStatus() == SessionStatus.OPEN)
                .filter(session -> !session.getOpensAt().isAfter(now))
                .filter(session -> session.getClosesAt().isAfter(now) && !session.getClosesAt().isAfter(cutoff))
                .map(SessionReminderService::toClosingSoonView);
    }

    private static ClosingSoonSessionView toClosingSoonView(ExamSession session) {
        return new ClosingSoonSessionView(session.getPublicId(), session.getTenantId(), session.getName(),
                session.getOpensAt(), session.getClosesAt());
    }
}
