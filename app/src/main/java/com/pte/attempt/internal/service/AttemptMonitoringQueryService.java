package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptHeartbeat;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.MonitoringAttemptSummaryView;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.attempt.dto.response.MonitoringRosterAggregateView;
import com.pte.attempt.dto.response.ProctorIntegrityReportView;
import com.pte.attempt.internal.repository.AttemptHeartbeatRepository;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Proctor-scoped read queries for latest attempt statuses, bounded student history,
 * whole-session aggregates joining the exported Session membership view, and integrity checks.
 */
@Service
public class AttemptMonitoringQueryService {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;

    private static final String ROSTER_AGGREGATE_SQL = """
            SELECT
                COUNT(m.student_public_id) AS total_enrolled,
                COUNT(CASE WHEN la.status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
                COUNT(CASE WHEN la.status = 'SUBMITTED' THEN 1 END) AS submitted_count,
                COUNT(CASE WHEN la.status = 'CREATED' THEN 1 END) AS created_count,
                COUNT(CASE WHEN la.status IS NULL THEN 1 END) AS not_started_count
            FROM session_monitoring_enrollment_membership m
            LEFT JOIN (
                SELECT a.student_public_id, a.status
                FROM exam_attempts a
                JOIN (
                    SELECT sub.student_public_id, MAX(sub.attempt_number) AS max_att
                    FROM exam_attempts sub
                    WHERE sub.session_public_id = ?
                      AND sub.tenant_id = ?
                      AND sub.deleted = FALSE
                    GROUP BY sub.student_public_id
                ) latest ON a.student_public_id = latest.student_public_id AND a.attempt_number = latest.max_att
                WHERE a.session_public_id = ?
                  AND a.tenant_id = ?
                  AND a.deleted = FALSE
            ) la ON m.student_public_id = la.student_public_id
            WHERE m.exam_session_public_id = ?
              AND m.tenant_id = ?
            """;

    private static final String DUPLICATE_MEMBERSHIP_SQL = """
            SELECT student_public_id
            FROM session_monitoring_enrollment_membership
            WHERE exam_session_public_id = ? AND tenant_id = ?
            GROUP BY student_public_id
            HAVING COUNT(*) > 1
            """;

    private static final String TIED_ATTEMPT_NUMBER_SQL = """
            SELECT student_public_id
            FROM exam_attempts
            WHERE session_public_id = ? AND tenant_id = ? AND deleted = FALSE
            GROUP BY student_public_id, attempt_number
            HAVING COUNT(*) > 1
            """;

    private static final String HISTORICAL_ORPHAN_ATTEMPTS_SQL = """
            SELECT a.public_id
            FROM exam_attempts a
            LEFT JOIN session_monitoring_enrollment_membership m
              ON a.session_public_id = m.exam_session_public_id
             AND a.student_public_id = m.student_public_id
             AND a.tenant_id = m.tenant_id
            WHERE a.session_public_id = ?
              AND a.tenant_id = ?
              AND a.deleted = FALSE
              AND m.student_public_id IS NULL
            """;

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptHeartbeatRepository attemptHeartbeatRepository;
    private final JdbcTemplate jdbcTemplate;

    public AttemptMonitoringQueryService(
            ExamAttemptRepository examAttemptRepository,
            AttemptHeartbeatRepository attemptHeartbeatRepository,
            JdbcTemplate jdbcTemplate) {
        this.examAttemptRepository = examAttemptRepository;
        this.attemptHeartbeatRepository = attemptHeartbeatRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Batch latest attempt resolution for a page of enrolled students on an exam session.
     * Guaranteed bounded query count (1 attempts query + 1 heartbeats query), independent of student count.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MonitoringAttemptView> findLatestAttemptsForStudents(
            UUID sessionPublicId, UUID tenantId, Collection<UUID> studentPublicIds) {
        if (sessionPublicId == null || tenantId == null || studentPublicIds == null || studentPublicIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> targetStudentIds = studentPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetStudentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ExamAttempt> attempts = examAttemptRepository
                .findBySessionPublicIdAndTenantIdAndStudentPublicIdInAndDeletedFalseOrderByAttemptNumberDesc(
                        sessionPublicId, tenantId, targetStudentIds);

        Map<UUID, List<ExamAttempt>> attemptsByStudent = attempts.stream()
                .collect(Collectors.groupingBy(ExamAttempt::getStudentPublicId));

        Set<Long> latestAttemptIds = attemptsByStudent.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getId())
                .collect(Collectors.toSet());

        Map<Long, Instant> heartbeatMap = latestAttemptIds.isEmpty()
                ? Collections.emptyMap()
                : attemptHeartbeatRepository.findByAttemptIdIn(latestAttemptIds).stream()
                .filter(h -> h.getAttempt() != null && h.getLastSeenAt() != null)
                .collect(Collectors.toMap(
                        h -> h.getAttempt().getId(),
                        AttemptHeartbeat::getLastSeenAt,
                        (existing, replacement) -> existing));

        Map<UUID, MonitoringAttemptView> result = new HashMap<>();
        for (UUID studentId : targetStudentIds) {
            List<ExamAttempt> studentAttempts = attemptsByStudent.get(studentId);
            if (studentAttempts == null || studentAttempts.isEmpty()) {
                result.put(studentId, new MonitoringAttemptView(null, studentId, null, null, null, null, null, 0));
            } else {
                ExamAttempt latest = studentAttempts.get(0);
                Instant lastSeenAt = heartbeatMap.get(latest.getId());
                result.put(studentId, new MonitoringAttemptView(
                        latest.getPublicId(),
                        studentId,
                        latest.getAttemptNumber(),
                        latest.getStatus(),
                        latest.getStartedAt(),
                        latest.getSubmittedAt(),
                        lastSeenAt,
                        studentAttempts.size()));
            }
        }

        return result;
    }

    /**
     * Bounded historical attempt list for a specific enrolled student on an exam session.
     */
    @Transactional(readOnly = true)
    public PagedResult<MonitoringAttemptSummaryView> findAttemptHistoryForStudent(
            UUID sessionPublicId, UUID studentPublicId, UUID tenantId, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "attemptNumber")
                        .and(Sort.by(Sort.Direction.ASC, "publicId")));

        Page<ExamAttempt> attemptPage = examAttemptRepository
                .findBySessionPublicIdAndStudentPublicIdAndTenantIdAndDeletedFalse(
                        sessionPublicId, studentPublicId, tenantId, pageable);

        Set<Long> attemptIds = attemptPage.getContent().stream()
                .map(ExamAttempt::getId)
                .collect(Collectors.toSet());

        Map<Long, Instant> heartbeatMap = attemptIds.isEmpty()
                ? Collections.emptyMap()
                : attemptHeartbeatRepository.findByAttemptIdIn(attemptIds).stream()
                .filter(h -> h.getAttempt() != null && h.getLastSeenAt() != null)
                .collect(Collectors.toMap(
                        h -> h.getAttempt().getId(),
                        AttemptHeartbeat::getLastSeenAt,
                        (existing, replacement) -> existing));

        List<MonitoringAttemptSummaryView> items = attemptPage.getContent().stream()
                .map(a -> new MonitoringAttemptSummaryView(
                        a.getPublicId(),
                        a.getStudentPublicId(),
                        a.getAttemptNumber(),
                        a.getStatus(),
                        a.getStartedAt(),
                        a.getSubmittedAt(),
                        heartbeatMap.get(a.getId())))
                .toList();

        PageMeta meta = new PageMeta(
                attemptPage.getNumber(),
                attemptPage.getSize(),
                attemptPage.getTotalElements(),
                attemptPage.getTotalPages(),
                attemptPage.isFirst(),
                attemptPage.isLast(),
                attemptPage.hasNext(),
                attemptPage.hasPrevious());

        return new PagedResult<>(items, meta);
    }

    /**
     * Whole-session roster status aggregate joining the exported Session membership view.
     * Excludes orphan historical attempts and counts each enrolled student once by their latest attempt.
     */
    @Transactional(readOnly = true)
    public MonitoringRosterAggregateView getRosterAggregate(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return new MonitoringRosterAggregateView(0, 0, 0, 0, 0);
        }

        MonitoringRosterAggregateView view = jdbcTemplate.query(
                ROSTER_AGGREGATE_SQL,
                rs -> {
                    if (rs.next()) {
                        return new MonitoringRosterAggregateView(
                                rs.getLong("total_enrolled"),
                                rs.getLong("not_started_count"),
                                rs.getLong("in_progress_count"),
                                rs.getLong("submitted_count"),
                                rs.getLong("created_count"));
                    }
                    return new MonitoringRosterAggregateView(0, 0, 0, 0, 0);
                },
                sessionPublicId, tenantId, sessionPublicId, tenantId, sessionPublicId, tenantId);

        return view != null ? view : new MonitoringRosterAggregateView(0, 0, 0, 0, 0);
    }

    /**
     * Inspects data integrity for an exam session: duplicate active memberships, tied attempt numbers,
     * and historical orphan attempts.
     */
    @Transactional(readOnly = true)
    public ProctorIntegrityReportView checkIntegrity(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return new ProctorIntegrityReportView(List.of(), List.of(), List.of());
        }

        List<UUID> duplicateMemberships = jdbcTemplate.query(
                DUPLICATE_MEMBERSHIP_SQL,
                (rs, rowNum) -> rs.getObject("student_public_id", UUID.class),
                sessionPublicId, tenantId);

        List<UUID> tiedAttempts = jdbcTemplate.query(
                TIED_ATTEMPT_NUMBER_SQL,
                (rs, rowNum) -> rs.getObject("student_public_id", UUID.class),
                sessionPublicId, tenantId);

        List<UUID> orphanAttempts = jdbcTemplate.query(
                HISTORICAL_ORPHAN_ATTEMPTS_SQL,
                (rs, rowNum) -> rs.getObject("public_id", UUID.class),
                sessionPublicId, tenantId);

        return new ProctorIntegrityReportView(duplicateMemberships, tiedAttempts, orphanAttempts);
    }
}
