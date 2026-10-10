package com.pte.enrollment.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.dto.response.StudentAttemptHistoryView;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.enrollment.dto.response.StudentAssignmentView;
import com.pte.enrollment.internal.dto.response.StudentAttemptHistoryResponse;
import com.pte.enrollment.internal.dto.response.StudentDetailResponse;
import com.pte.enrollment.internal.dto.response.StudentPerformanceResponse;
import com.pte.enrollment.internal.dto.response.StudentSkillPerformanceResponse;
import com.pte.enrollment.internal.exception.InvalidStudentRosterQueryException;
import com.pte.enrollment.internal.exception.StudentNotFoundException;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.reporting.ReportingService;
import com.pte.reporting.dto.response.ReportSkillScoreView;
import com.pte.reporting.dto.response.StudentReportSummaryView;
import com.pte.session.SessionService;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Composes the host-only student workspace through public module boundaries. */
@Service
public class StudentWorkspaceQueryService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final List<String> SKILLS = List.of("LISTENING", "READING", "SPEAKING", "WRITING");

    private final IdentityService identityService;
    private final EnrollmentModuleService enrollmentModuleService;
    private final AttemptService attemptService;
    private final SessionService sessionService;
    private final ReportingService reportingService;

    public StudentWorkspaceQueryService(IdentityService identityService,
            EnrollmentModuleService enrollmentModuleService,
            AttemptService attemptService,
            SessionService sessionService,
            ReportingService reportingService) {
        this.identityService = identityService;
        this.enrollmentModuleService = enrollmentModuleService;
        this.attemptService = attemptService;
        this.sessionService = sessionService;
        this.reportingService = reportingService;
    }

    @Transactional(readOnly = true)
    public StudentDetailResponse getDetail(UUID studentPublicId, CurrentUser caller) {
        User student = requireStudent(studentPublicId, caller);
        StudentAssignmentView assignment = enrollmentModuleService.findCurrentStudentAssignment(
                caller.tenantId(), studentPublicId);
        return new StudentDetailResponse(student.getPublicId(), student.getUsername(), student.getEmail(),
                student.getFullName(), student.getTenantId(), student.getStatus().name(),
                student.getRoles().stream().map(Role::name).sorted().toList(), student.getStudentCode(),
                student.getPhone(), student.getDateOfBirth(), student.isMustChangePassword(), assignment);
    }

    @Transactional(readOnly = true)
    public PagedResult<StudentAttemptHistoryResponse> getHistory(UUID studentPublicId, int requestedPage,
            int requestedSize, LocalDate fromDate, LocalDate toDate, String status, CurrentUser caller) {
        requireStudent(studentPublicId, caller);
        DateRange range = dateRange(fromDate, toDate);
        AttemptStatus parsedStatus = parseStatus(status);
        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        PagedResult<StudentAttemptHistoryView> attempts = attemptService.findStudentHistory(studentPublicId,
                caller.tenantId(), page, size, range.from(), range.to(), parsedStatus);
        List<UUID> attemptIds = attempts.data().stream().map(StudentAttemptHistoryView::attemptPublicId).toList();
        Map<UUID, SessionSummaryView> sessions = sessionService.findSummaries(
                        attempts.data().stream().map(StudentAttemptHistoryView::sessionPublicId).toList(), caller.tenantId())
                .stream().collect(Collectors.toMap(SessionSummaryView::publicId, value -> value));
        Map<UUID, StudentReportSummaryView> reports = reportingService.findAttemptSummaries(
                        caller.tenantId(), studentPublicId, attemptIds)
                .stream().collect(Collectors.toMap(StudentReportSummaryView::attemptPublicId, value -> value));
        List<StudentAttemptHistoryResponse> rows = attempts.data().stream()
                .map(attempt -> toHistoryResponse(attempt, sessions.get(attempt.sessionPublicId()),
                        reports.get(attempt.attemptPublicId())))
                .toList();
        return new PagedResult<>(rows, attempts.meta());
    }

    @Transactional(readOnly = true)
    public StudentPerformanceResponse getPerformance(UUID studentPublicId, LocalDate fromDate,
            LocalDate toDate, CurrentUser caller) {
        requireStudent(studentPublicId, caller);
        DateRange range = dateRange(fromDate, toDate);
        PagedResult<StudentAttemptHistoryView> latest = attemptService.findStudentHistory(studentPublicId,
                caller.tenantId(), 0, 1, range.from(), range.to(), null);
        List<StudentReportSummaryView> reports = reportingService.findPublishedImmutableSummaries(
                caller.tenantId(), studentPublicId, range.from(), range.to());

        List<Integer> overallScores = reports.stream()
                .map(StudentReportSummaryView::overall)
                .filter(score -> score != null && score.sufficientData() && score.score() != null)
                .map(ReportSkillScoreView::score)
                .toList();
        Map<String, List<Integer>> skillScores = new HashMap<>();
        for (String skill : SKILLS) {
            skillScores.put(skill, reports.stream()
                    .flatMap(report -> report.skills().stream())
                    .filter(score -> skill.equals(score.skill()) && score.sufficientData() && score.score() != null)
                    .map(ReportSkillScoreView::score)
                    .toList());
        }
        List<StudentSkillPerformanceResponse> skills = SKILLS.stream()
                .map(skill -> averageSkill(skill, skillScores.get(skill)))
                .toList();
        Integer averageOverall = average(overallScores);
        Instant latestAttemptAt = latest.data().isEmpty() ? null : latest.data().get(0).createdAt();
        return new StudentPerformanceResponse(latest.meta().totalElements(), latestAttemptAt, averageOverall,
                averageOverall != null, skills, "PUBLISHED_IMMUTABLE_REPORTS");
    }

    private User requireStudent(UUID studentPublicId, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new StudentNotFoundException();
        }
        return identityService.findStudentByTenant(studentPublicId, caller.tenantId())
                .orElseThrow(StudentNotFoundException::new);
    }

    private StudentAttemptHistoryResponse toHistoryResponse(StudentAttemptHistoryView attempt,
            SessionSummaryView session, StudentReportSummaryView report) {
        String reportState = report == null ? "NOT_AVAILABLE"
                : !report.published() ? "UNPUBLISHED"
                : report.immutableSnapshot() ? "PUBLISHED" : "PUBLISHED_LEGACY";
        Integer overallScore = report != null && report.immutableSnapshot() && report.overall() != null
                && report.overall().sufficientData() ? report.overall().score() : null;
        boolean overallSufficient = report != null && report.immutableSnapshot() && report.overall() != null
                && report.overall().sufficientData();
        return new StudentAttemptHistoryResponse(attempt.attemptPublicId(), attempt.sessionPublicId(),
                session == null ? "Exam session" : session.name(), session == null ? null : session.sessionCode(),
                session == null ? null : session.status(), attempt.attemptNumber(), attempt.status(),
                attempt.createdAt(), attempt.startedAt(), attempt.submittedAt(), reportState, report != null,
                overallScore, overallSufficient);
    }

    private StudentSkillPerformanceResponse averageSkill(String skill, Collection<Integer> values) {
        List<Integer> scores = values == null ? List.of() : values.stream().toList();
        Integer average = average(scores);
        return new StudentSkillPerformanceResponse(skill, average, average != null, scores.size());
    }

    private Integer average(Collection<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private AttemptStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return AttemptStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidStudentRosterQueryException();
        }
    }

    private DateRange dateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidStudentRosterQueryException();
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(from, to);
    }

    private record DateRange(Instant from, Instant to) {
    }
}
