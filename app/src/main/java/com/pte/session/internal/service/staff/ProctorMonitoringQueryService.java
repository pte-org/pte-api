package com.pte.session.internal.service.staff;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.dto.response.MonitoringEnrollmentView;
import com.pte.session.dto.response.ProctorAssignedSessionsPageView;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.dto.response.ProctorWorkspaceOverviewView;
import com.pte.session.dto.response.ProctorWorkspaceSessionView;
import com.pte.session.internal.dto.response.proctor.ProctorOverviewCountsResponse;
import com.pte.session.internal.exception.staff.InvalidProctorScopeException;
import com.pte.session.internal.exception.capacity.InvalidTimezoneException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.session.internal.repository.ProctorOverviewCountsProjection;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Proctor-scoped read queries for assigned sessions, monitoring metadata, and enrollment rosters.
 */
@Service
public class ProctorMonitoringQueryService {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;

    private final ExamSessionProctorRepository proctorRepository;
    private final ExamRegistrationRepository registrationRepository;
    private final ExamSessionRepository examSessionRepository;
    private final Clock clock;

    @Autowired
    public ProctorMonitoringQueryService(
            ExamSessionProctorRepository proctorRepository,
            ExamRegistrationRepository registrationRepository,
            ExamSessionRepository examSessionRepository,
            Clock clock) {
        this.proctorRepository = proctorRepository;
        this.registrationRepository = registrationRepository;
        this.examSessionRepository = examSessionRepository;
        this.clock = clock;
    }

    /**
     * Reads monitoring metadata for an exam session if the proctor is assigned to it within the caller's tenant.
     */
    @Transactional(readOnly = true)
    public Optional<ProctorMonitoringSessionView> getMonitoringSession(
            UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        if (sessionPublicId == null || proctorPublicId == null || tenantId == null) {
            return Optional.empty();
        }
        return proctorRepository.findAssignment(sessionPublicId, proctorPublicId, tenantId)
                .map(ProctorSessionViews::toSessionView);
    }

    /**
     * Lists assigned sessions for a proctor in the caller's tenant, ordered by opensAt ASC nulls-last then publicId ASC.
     */
    @Transactional(readOnly = true)
    public PagedResult<ProctorMonitoringSessionView> getAssignedSessions(
            UUID proctorPublicId, UUID tenantId, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("session.opensAt").nullsLast())
                        .and(Sort.by("session.publicId").ascending()));

        Page<ExamSessionProctor> assignmentPage = proctorRepository.findAssignedSessions(
                proctorPublicId, tenantId, pageable);

        List<ProctorMonitoringSessionView> items = assignmentPage.getContent().stream()
                .map(ProctorSessionViews::toSessionView)
                .toList();

        PageMeta meta = new PageMeta(
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(),
                assignmentPage.isFirst(),
                assignmentPage.isLast(),
                assignmentPage.hasNext(),
                assignmentPage.hasPrevious());

        return new PagedResult<>(items, meta);
    }

    /**
     * Paged active student enrollments for an exam session, stable by enrollment publicId ASC.
     */
    @Transactional(readOnly = true)
    public PagedResult<MonitoringEnrollmentView> getEnrollments(
            UUID sessionPublicId, UUID tenantId, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);
        Pageable pageable = PageRequest.of(page, size, Sort.by("publicId").ascending());

        Page<com.pte.session.domain.ExamRegistration> enrollmentPage = registrationRepository
                .findEnrolledBySessionPublicId(sessionPublicId, tenantId, pageable);

        List<MonitoringEnrollmentView> items = enrollmentPage.getContent().stream()
                .map(e -> new MonitoringEnrollmentView(e.getPublicId(), e.getStudentPublicId()))
                .toList();

        PageMeta meta = new PageMeta(
                enrollPage(enrollmentPage),
                enrollmentPage.getSize(),
                enrollmentPage.getTotalElements(),
                enrollmentPage.getTotalPages(),
                enrollmentPage.isFirst(),
                enrollmentPage.isLast(),
                enrollmentPage.hasNext(),
                enrollmentPage.hasPrevious());

        return new PagedResult<>(items, meta);
    }

    private int enrollPage(Page<?> page) {
        return page.getNumber();
    }

    @Transactional(readOnly = true)
    public List<UUID> findEnrolledStudentIds(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return List.of();
        }
        return registrationRepository.findEnrolledStudentPublicIds(sessionPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public long countEnrollments(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return 0L;
        }
        return registrationRepository.countEnrolledBySessionPublicId(sessionPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isStudentEnrolled(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        if (sessionPublicId == null || studentPublicId == null || tenantId == null) {
            return false;
        }
        return registrationRepository.isStudentEnrolled(sessionPublicId, studentPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isProctorAssigned(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        if (sessionPublicId == null || proctorPublicId == null || tenantId == null) {
            return false;
        }
        return proctorRepository.isProctorAssigned(sessionPublicId, proctorPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public ProctorWorkspaceOverviewView getWorkspaceOverview(
            UUID proctorPublicId, UUID tenantId, String timezoneStr) {
        if (proctorPublicId == null || tenantId == null || timezoneStr == null || timezoneStr.isBlank()) {
            throw new InvalidTimezoneException();
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezoneStr.trim());
        } catch (DateTimeException | IllegalArgumentException ex) {
            throw new InvalidTimezoneException();
        }

        Instant asOf = clock.instant();
        ZonedDateTime zdt = asOf.atZone(zoneId);
        Instant dayStart = zdt.toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant dayEnd = zdt.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

        ProctorOverviewCountsProjection projection = proctorRepository
                .countWorkspaceOverview(proctorPublicId, tenantId, asOf, dayStart, dayEnd);
        ProctorOverviewCountsResponse counts = new ProctorOverviewCountsResponse(
                projection.getAccessible(), projection.getUpcoming(), projection.getToday());

        List<ExamSessionProctor> priorityAssignments = proctorRepository
                .findPrioritySessions(proctorPublicId, tenantId, asOf, PageRequest.of(0, 5));
        List<ProctorWorkspaceSessionView> prioritySessions = priorityAssignments.stream()
                .map(pa -> ProctorSessionViews.toWorkspaceSessionView(pa, asOf))
                .toList();

        return new ProctorWorkspaceOverviewView(
                asOf, zoneId.getId(), dayStart, dayEnd, counts, prioritySessions);
    }

    @Transactional(readOnly = true)
    public ProctorAssignedSessionsPageView getAssignedSessions(
            UUID proctorPublicId, UUID tenantId, String scopeStr, String timezoneStr,
            String sortStr, int requestedPage, int requestedSize) {
        String scope = (scopeStr == null || scopeStr.isBlank()) ? "ALL" : scopeStr.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "ACCESSIBLE", "UPCOMING", "TODAY").contains(scope)) {
            throw new InvalidProctorScopeException();
        }

        ZoneId zoneId = null;
        Instant dayStart = null;
        Instant dayEnd = null;
        if (timezoneStr != null && !timezoneStr.isBlank()) {
            try {
                zoneId = ZoneId.of(timezoneStr.trim());
            } catch (DateTimeException | IllegalArgumentException ex) {
                throw new InvalidTimezoneException();
            }
        } else if ("TODAY".equals(scope)) {
            throw new InvalidTimezoneException();
        }

        Instant asOf = clock.instant();
        if (zoneId != null) {
            ZonedDateTime zdt = asOf.atZone(zoneId);
            dayStart = zdt.toLocalDate().atStartOfDay(zoneId).toInstant();
            dayEnd = zdt.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();
        }

        boolean usePrioritySort = "PRIORITY".equalsIgnoreCase(sortStr);
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);
        Pageable pageable = PageRequest.of(page, size);

        Page<ExamSessionProctor> assignmentPage = proctorRepository.findScopedAssignedSessions(
                proctorPublicId, tenantId, scope, asOf,
                dayStart != null ? dayStart : Instant.EPOCH,
                dayEnd != null ? dayEnd : Instant.EPOCH,
                usePrioritySort, pageable);

        List<ProctorWorkspaceSessionView> items = assignmentPage.getContent().stream()
                .map(pa -> ProctorSessionViews.toWorkspaceSessionView(pa, asOf))
                .toList();

        PageMeta meta = new PageMeta(
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(),
                assignmentPage.isFirst(),
                assignmentPage.isLast(),
                assignmentPage.hasNext(),
                assignmentPage.hasPrevious());

        return new ProctorAssignedSessionsPageView(
                items, meta, asOf, zoneId != null ? zoneId.getId() : null, dayStart, dayEnd);
    }

    public static boolean canMonitor(ExamSession s, Instant asOf) {
        return ProctorSessionViews.canMonitor(s, asOf);
    }

    public static String monitoringBlockingReason(ExamSession s, Instant asOf) {
        return ProctorSessionViews.monitoringBlockingReason(s, asOf);
    }
}
