package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.MonitoringAttemptSummaryView;
import com.pte.attempt.dto.response.MonitoringAttemptView;
import com.pte.attempt.dto.response.MonitoringRosterAggregateView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.internal.dto.response.MonitoringSnapshotResponse;
import com.pte.proctoring.internal.dto.response.MonitoringStudentResponse;
import com.pte.proctoring.internal.dto.response.MonitoringSummaryResponse;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ViolationEventRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.MonitoringEnrollmentView;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authoritative monitoring coordinator combining Session metadata, enrollment pagination,
 * Attempt batch latest/aggregates, Identity display names, and manual Proctor violation counts.
 */
@Service
public class MonitoringQueryService {

    private final ProctorAccessPolicy proctorAccessPolicy;
    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final IdentityService identityService;
    private final ViolationEventRepository violationEventRepository;

    public MonitoringQueryService(
            ProctorAccessPolicy proctorAccessPolicy,
            SessionService sessionService,
            AttemptService attemptService,
            IdentityService identityService,
            ViolationEventRepository violationEventRepository) {
        this.proctorAccessPolicy = proctorAccessPolicy;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.identityService = identityService;
        this.violationEventRepository = violationEventRepository;
    }

    /**
     * Executes in a read-only REPEATABLE_READ transaction so the snapshot is internally coherent.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonitoringSnapshotResponse getSnapshot(
            UUID sessionPublicId, int requestedPage, int requestedSize, CurrentUser caller) {
        UUID tenantId = proctorAccessPolicy.authorizeProctorAssigned(sessionPublicId, caller);

        ProctorMonitoringSessionView sessionView = sessionService
                .getMonitoringSession(sessionPublicId, caller.userId(), tenantId)
                .orElseThrow(() -> new AccessDeniedException("Session access denied"));

        PagedResult<MonitoringEnrollmentView> enrollments = sessionService
                .getMonitoringEnrollments(sessionPublicId, tenantId, requestedPage, requestedSize);

        List<UUID> studentIds = enrollments.data().stream()
                .map(MonitoringEnrollmentView::studentPublicId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, StudentDisplayNameView> displayNames = studentIds.isEmpty()
                ? Collections.emptyMap()
                : identityService.getStudentDisplayNames(tenantId, studentIds);

        Map<UUID, MonitoringAttemptView> attempts = studentIds.isEmpty()
                ? Collections.emptyMap()
                : attemptService.getLatestAttemptsForStudents(sessionPublicId, tenantId, studentIds);

        List<UUID> attemptIds = attempts.values().stream()
                .map(MonitoringAttemptView::attemptPublicId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, Long> violationCounts = attemptIds.isEmpty()
                ? Collections.emptyMap()
                : violationEventRepository.countViolationsByAttemptPublicIdIn(sessionPublicId, tenantId, attemptIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1],
                        (existing, replacement) -> existing));

        List<MonitoringStudentResponse> studentResponses = enrollments.data().stream()
                .map(enrollment -> {
                    UUID studentId = enrollment.studentPublicId();
                    StudentDisplayNameView nameView = displayNames.get(studentId);
                    String name = nameView != null ? nameView.displayName() : "Thí sinh";
                    String email = nameView != null ? nameView.email() : null;

                    MonitoringAttemptView att = attempts.get(studentId);
                    UUID attId = att != null ? att.attemptPublicId() : null;
                    Integer attNum = att != null ? att.attemptNumber() : null;
                    com.pte.attempt.domain.enums.AttemptStatus status = att != null ? att.status() : null;
                    Instant startedAt = att != null ? att.startedAt() : null;
                    Instant submittedAt = att != null ? att.submittedAt() : null;
                    Instant lastSeenAt = att != null ? att.lastSeenAt() : null;
                    int count = att != null ? att.attemptCount() : 0;
                    long violations = attId != null ? violationCounts.getOrDefault(attId, 0L) : 0L;

                    return new MonitoringStudentResponse(
                            studentId, name, email, attId, attNum, status, startedAt, submittedAt, lastSeenAt, count, violations);
                })
                .toList();

        MonitoringRosterAggregateView aggregate = attemptService.getRosterAggregate(sessionPublicId, tenantId);
        Instant snapshotAt = Instant.now();

        MonitoringSummaryResponse summary = new MonitoringSummaryResponse(
                aggregate.registeredCount(),
                aggregate.notStartedCount(),
                aggregate.inProgressCount(),
                aggregate.submittedCount(),
                aggregate.createdCount(),
                snapshotAt);

        PagedResult<MonitoringStudentResponse> studentPagedResult = new PagedResult<>(studentResponses, enrollments.meta());
        return new MonitoringSnapshotResponse(sessionView, summary, studentPagedResult, snapshotAt);
    }

    /**
     * Reads historical attempts for an enrolled student on the exam session.
     */
    @Transactional(readOnly = true)
    public PagedResult<MonitoringAttemptSummaryView> getStudentAttemptHistory(
            UUID sessionPublicId, UUID studentPublicId, int page, int size, CurrentUser caller) {
        UUID tenantId = proctorAccessPolicy.authorizeProctorAssigned(sessionPublicId, caller);
        if (!sessionService.isStudentEnrolledInSession(sessionPublicId, studentPublicId, tenantId)) {
            throw new AccessDeniedException("Student is not enrolled in this session");
        }
        return attemptService.getAttemptHistoryForStudent(sessionPublicId, studentPublicId, tenantId, page, size);
    }
}
