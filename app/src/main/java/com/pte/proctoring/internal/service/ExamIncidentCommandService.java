package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.proctoring.internal.constant.ExamIncidentConstants;
import com.pte.proctoring.internal.mapper.ExamIncidentMapper;
import com.pte.proctoring.internal.policy.ExamIncidentAccessPolicy;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamIncidentCommandService {

    private final ExamIncidentRepository incidentRepository;
    private final IncidentHistoryLog historyLog;
    private final IdentityService identityService;
    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ExamIncidentAccessPolicy accessPolicy;
    private final ExamIncidentMapper mapper;
    private final Clock clock;

    public ExamIncidentCommandService(
            ExamIncidentRepository incidentRepository,
            IncidentHistoryLog historyLog,
            IdentityService identityService,
            SessionService sessionService,
            AttemptService attemptService,
            ExamIncidentAccessPolicy accessPolicy,
            ExamIncidentMapper mapper,
            Clock clock) {
        this.incidentRepository = incidentRepository;
        this.historyLog = historyLog;
        this.identityService = identityService;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public IncidentView createIncident(
            UUID sessionPublicId,
            CreateIncidentRequest request,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        validateCreateTarget(sessionPublicId, request, access.tenantId());

        ExamIncident incident = new ExamIncident();
        incident.setPublicId(UUID.randomUUID());
        incident.setDetectedAt(clock.instant());
        incident.setTenantId(access.tenantId());
        incident.setSessionPublicId(sessionPublicId);
        incident.setStudentPublicId(request.studentPublicId());
        incident.setAttemptPublicId(request.attemptPublicId());
        incident.setOrigin(IncidentOrigin.REPORTED);
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description().trim());
        incident.setType(request.category().trim().toUpperCase());
        incident.setSeverity(request.severity().trim().toUpperCase());
        incident.setStatus(ExamIncidentConstants.STATUS_NEW);
        incident.setReporterRole(access.actorRole());
        incident.setReporterName(access.actorName());
        incident.setReporterPublicId(access.actorPublicId());
        incident.setCurrentOwnerRole(ExamIncidentConstants.OWNER_HOST_ADMIN);
        incident.setCurrentOwnerName(null);
        incident.setResolutionSummary(null);

        ExamIncident saved = incidentRepository.save(incident);
        historyLog.record(
                caller,
                saved.getPublicId(),
                access,
                ExamIncidentConstants.ACTION_CREATE,
                null,
                ExamIncidentConstants.STATUS_NEW,
                request.description());

        return mapper.toView(saved, resolveStudentName(access.tenantId(), saved.getStudentPublicId()));
    }

    @Transactional
    public IncidentView updateIncidentStatus(
            UUID sessionPublicId,
            UUID incidentPublicId,
            UpdateIncidentStatusRequest request,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        ExamIncident incident = findIncident(sessionPublicId, incidentPublicId, access.tenantId());

        String previousStatus = incident.getStatus();
        String newStatus = request.status().trim().toUpperCase();
        validateStatus(previousStatus, newStatus);

        if (access.proctor()) {
            applyProctorTransition(incident, previousStatus, newStatus, request);
        } else {
            applyHostOrPlatformTransition(incident, newStatus, request);
        }

        incident.setStatus(newStatus);
        if (request.resolutionSummary() != null) {
            incident.setResolutionSummary(request.resolutionSummary().trim());
        }

        ExamIncident saved = incidentRepository.save(incident);
        historyLog.record(
                caller,
                saved.getPublicId(),
                access,
                ExamIncidentConstants.ACTION_UPDATE_STATUS,
                previousStatus,
                newStatus,
                request.notes());

        return mapper.toView(saved, resolveStudentName(access.tenantId(), saved.getStudentPublicId()));
    }

    private void validateCreateTarget(
            UUID sessionPublicId,
            CreateIncidentRequest request,
            UUID tenantId) {
        if (request.studentPublicId() != null
                && !sessionService.isStudentEnrolledInSession(sessionPublicId, request.studentPublicId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamIncidentConstants.STUDENT_NOT_ENROLLED);
        }

        if (request.attemptPublicId() != null
                && !attemptService.attemptBelongsToSessionAndStudent(
                request.attemptPublicId(), sessionPublicId, request.studentPublicId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamIncidentConstants.ATTEMPT_NOT_IN_SESSION);
        }
    }

    private void validateStatus(String previousStatus, String newStatus) {
        if (!ExamIncidentConstants.VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamIncidentConstants.INVALID_STATUS);
        }
        if (ExamIncidentConstants.STATUS_CLOSED.equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamIncidentConstants.CLOSED_INCIDENT_IMMUTABLE);
        }
    }

    private void applyProctorTransition(
            ExamIncident incident,
            String previousStatus,
            String newStatus,
            UpdateIncidentStatusRequest request) {
        if (ExamIncidentConstants.STATUS_RESOLVED.equalsIgnoreCase(previousStatus)
                || ExamIncidentConstants.STATUS_ESCALATED.equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    String.format(ExamIncidentConstants.PROCTOR_CANNOT_MODIFY_STATUS, previousStatus));
        }
        if (!ExamIncidentConstants.STATUS_NEW.equalsIgnoreCase(newStatus)
                && !ExamIncidentConstants.STATUS_IN_PROGRESS.equalsIgnoreCase(newStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    ExamIncidentConstants.PROCTOR_STATUS_TRANSITION_FORBIDDEN);
        }
        if (ExamIncidentConstants.STATUS_IN_PROGRESS.equalsIgnoreCase(previousStatus)
                && ExamIncidentConstants.STATUS_NEW.equalsIgnoreCase(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamIncidentConstants.PROCTOR_CANNOT_REOPEN);
        }
        if (request.receiverRole() != null && !request.receiverRole().isBlank()
                && !ExamIncidentConstants.OWNER_HOST_ADMIN.equals(request.receiverRole().trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    ExamIncidentConstants.PROCTOR_ROUTE_ONLY_HOST);
        }
        incident.setCurrentOwnerRole(ExamIncidentConstants.OWNER_HOST_ADMIN);
    }

    private void applyHostOrPlatformTransition(
            ExamIncident incident,
            String newStatus,
            UpdateIncidentStatusRequest request) {
        if (ExamIncidentConstants.STATUS_ESCALATED.equalsIgnoreCase(newStatus)) {
            incident.setCurrentOwnerRole(ExamIncidentConstants.OWNER_PLATFORM_ADMIN);
        } else if (request.receiverRole() != null && !request.receiverRole().isBlank()) {
            String receiverRole = request.receiverRole().trim().toUpperCase();
            if (!ExamIncidentConstants.ALLOWED_RECEIVER_ROLES.contains(receiverRole)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        ExamIncidentConstants.INVALID_RECEIVER_ROLE);
            }
            incident.setCurrentOwnerRole(receiverRole);
        } else if (ExamIncidentConstants.STATUS_RESOLVED.equalsIgnoreCase(newStatus)
                || ExamIncidentConstants.STATUS_CLOSED.equalsIgnoreCase(newStatus)) {
            incident.setCurrentOwnerRole(ExamIncidentConstants.OWNER_HOST_ADMIN);
        }
    }

    private ExamIncident findIncident(UUID sessionPublicId, UUID incidentPublicId, UUID tenantId) {
        return incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndOriginAndDeletedFalse(
                        incidentPublicId, sessionPublicId, tenantId, IncidentOrigin.REPORTED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ExamIncidentConstants.INCIDENT_NOT_FOUND));
    }

    private StudentDisplayNameView resolveStudentName(UUID tenantId, UUID studentId) {
        if (studentId == null) {
            return null;
        }
        try {
            Map<UUID, StudentDisplayNameView> names = identityService.getStudentDisplayNames(
                    tenantId, List.of(studentId));
            return names != null ? names.get(studentId) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
