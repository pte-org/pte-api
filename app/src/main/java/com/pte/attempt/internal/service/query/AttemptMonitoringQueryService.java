package com.pte.attempt.internal.service.query;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
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
}
