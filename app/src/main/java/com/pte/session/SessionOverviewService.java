package com.pte.session;

import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ExamSessionRepository.UpcomingSessionRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only session counts for dashboards. Counts and a small bounded list
 * only; every query is tenant-scoped and ignores soft-deleted rows. Uses only
 * this module's own repositories.
 */
@Service
public class SessionOverviewService {

    private static final List<SessionStatus> JOINABLE = List.of(SessionStatus.SCHEDULED, SessionStatus.OPEN);

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public SessionOverviewService(ExamSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** Session count per lifecycle status; every status is present (0 when none), in enum order. */
    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus(UUID tenantId) {
        Map<SessionStatus, Long> raw = new EnumMap<>(SessionStatus.class);
        for (Object[] row : sessionRepository.countByTenantGroupedByStatus(tenantId)) {
            raw.put((SessionStatus) row[0], ((Number) row[1]).longValue());
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (SessionStatus status : SessionStatus.values()) {
            result.put(status.name(), raw.getOrDefault(status, 0L));
        }
        return result;
    }

    /** Sessions created in {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long countCreatedBetween(UUID tenantId, Instant from, Instant to) {
        return sessionRepository.countCreatedBetween(tenantId, from, to);
    }

    /** Up to {@code limit} scheduled or open sessions that have not closed, soonest first, with enrolled counts. */
    @Transactional(readOnly = true)
    public List<UpcomingSessionView> upcoming(UUID tenantId, Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<UpcomingSessionRow> rows = sessionRepository.findUpcoming(
                tenantId, JOINABLE, now, PageRequest.of(0, limit));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> enrolledBySession = enrollmentRepository
                .countGroupedBySessionIds(rows.stream().map(UpcomingSessionRow::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
        return rows.stream()
                .map(row -> new UpcomingSessionView(row.getPublicId(), row.getName(), row.getStatus().name(),
                        row.getOpensAt(), row.getClosesAt(), enrolledBySession.getOrDefault(row.getId(), 0L),
                        row.getCapacity()))
                .toList();
    }
}
