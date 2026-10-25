package com.pte.attempt.internal.service.query;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.MonitoringAttemptSummaryView;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.attempt.dto.response.MonitoringRosterAggregateView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * and whole-session status aggregates.
 */
@Service
public class AttemptMonitoringQueryService {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;

    private static final int ROSTER_BATCH_SIZE = 500;

    private final ExamAttemptRepository examAttemptRepository;

    public AttemptMonitoringQueryService(
            ExamAttemptRepository examAttemptRepository) {
        this.examAttemptRepository = examAttemptRepository;
    }

    /**
     * Batch latest attempt resolution for a page of enrolled students on an exam session.
     * Guaranteed bounded query count (1 attempts query), independent of student count.
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

        Map<UUID, MonitoringAttemptView> result = new HashMap<>();
        for (UUID studentId : targetStudentIds) {
            List<ExamAttempt> studentAttempts = attemptsByStudent.get(studentId);
            if (studentAttempts == null || studentAttempts.isEmpty()) {
                result.put(studentId, new MonitoringAttemptView(null, studentId, null, null, null, null, null, 0));
            } else {
                ExamAttempt latest = studentAttempts.get(0);
                Instant lastSeenAt = latest.getLastSeenAt();
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

        List<MonitoringAttemptSummaryView> items = attemptPage.getContent().stream()
                .map(a -> new MonitoringAttemptSummaryView(
                        a.getPublicId(),
                        a.getStudentPublicId(),
                        a.getAttemptNumber(),
                        a.getStatus(),
                        a.getStartedAt(),
                        a.getSubmittedAt(),
                        a.getLastSeenAt()))
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
     * Whole-session roster status aggregate over the given enrolled students: each counts once, by their latest
     * attempt; attempts of students who are no longer enrolled never count.
     */
    @Transactional(readOnly = true)
    public MonitoringRosterAggregateView getRosterAggregate(UUID sessionPublicId, UUID tenantId,
            Collection<UUID> enrolledStudentPublicIds) {
        List<UUID> students = enrolledStudentPublicIds == null ? List.of()
                : enrolledStudentPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (sessionPublicId == null || tenantId == null || students.isEmpty()) {
            return new MonitoringRosterAggregateView(0, 0, 0, 0, 0);
        }
        long inProgress = 0;
        long submitted = 0;
        long created = 0;
        long started = 0;
        for (int from = 0; from < students.size(); from += ROSTER_BATCH_SIZE) {
            List<UUID> batch = students.subList(from, Math.min(from + ROSTER_BATCH_SIZE, students.size()));
            for (ExamAttemptRepository.LatestStatusRow row
                    : examAttemptRepository.findLatestStatuses(sessionPublicId, tenantId, batch)) {
                started++;
                switch (row.getStatus()) {
                    case IN_PROGRESS -> inProgress++;
                    case SUBMITTED -> submitted++;
                    case CREATED -> created++;
                    default -> { }
                }
            }
        }
        return new MonitoringRosterAggregateView(students.size(), students.size() - started, inProgress,
                submitted, created);
    }
}
