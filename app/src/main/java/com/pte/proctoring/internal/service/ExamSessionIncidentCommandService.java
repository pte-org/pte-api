package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamSessionIncident;
import com.pte.proctoring.domain.ExamSessionIncidentHistory;
import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.proctoring.internal.constant.ExamSessionIncidentConstants;
import com.pte.proctoring.internal.mapper.ExamSessionIncidentMapper;
import com.pte.proctoring.internal.policy.ExamSessionIncidentAccessPolicy;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.proctoring.internal.repository.ExamSessionIncidentHistoryRepository;
import com.pte.proctoring.internal.repository.ExamSessionIncidentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamSessionIncidentCommandService {

    private final ExamSessionIncidentRepository incidentRepository;
    private final ExamSessionIncidentHistoryRepository historyRepository;
    private final IdentityService identityService;
    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ExamSessionIncidentAccessPolicy accessPolicy;
    private final ExamSessionIncidentMapper mapper;

    public ExamSessionIncidentCommandService(
            ExamSessionIncidentRepository incidentRepository,
            ExamSessionIncidentHistoryRepository historyRepository,
            IdentityService identityService,
            SessionService sessionService,
            AttemptService attemptService,
            ExamSessionIncidentAccessPolicy accessPolicy,
            ExamSessionIncidentMapper mapper) {
        this.incidentRepository = incidentRepository;
        this.historyRepository = historyRepository;
        this.identityService = identityService;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
    }

    @Transactional
    public IncidentView createIncident(
            UUID sessionPublicId,
            CreateIncidentRequest request,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        validateCreateTarget(sessionPublicId, request, access.tenantId());

        ExamSessionIncident incident = new ExamSessionIncident();
        incident.setPublicId(UUID.randomUUID());
        incident.setTenantId(access.tenantId());
        incident.setSessionPublicId(sessionPublicId);
        incident.setStudentPublicId(request.studentPublicId());
        incident.setAttemptPublicId(request.attemptPublicId());
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description().trim());
        incident.setCategory(request.category().trim().toUpperCase());
        incident.setSeverity(request.severity().trim().toUpperCase());
        incident.setStatus(ExamSessionIncidentConstants.STATUS_NEW);
        incident.setReporterRole(access.actorRole());
        incident.setReporterName(access.actorName());
        incident.setReporterPublicId(access.actorPublicId());
        incident.setCurrentOwnerRole(ExamSessionIncidentConstants.OWNER_HOST_ADMIN);
        incident.setCurrentOwnerName(null);
        incident.setResolutionSummary(null);

        ExamSessionIncident saved = incidentRepository.save(incident);
        saveHistory(
                saved,
                access,
                ExamSessionIncidentConstants.ACTION_CREATE,
                null,
                ExamSessionIncidentConstants.STATUS_NEW,
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
        ExamSessionIncident incident = findIncident(sessionPublicId, incidentPublicId, access.tenantId());

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

        ExamSessionIncident saved = incidentRepository.save(incident);
        saveHistory(
                saved,
                access,
                ExamSessionIncidentConstants.ACTION_UPDATE_STATUS,
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
                    ExamSessionIncidentConstants.STUDENT_NOT_ENROLLED);
        }

        if (request.attemptPublicId() != null
                && !attemptService.attemptBelongsToSessionAndStudent(
                request.attemptPublicId(), sessionPublicId, request.studentPublicId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamSessionIncidentConstants.ATTEMPT_NOT_IN_SESSION);
        }
    }

    private void validateStatus(String previousStatus, String newStatus) {
        if (!ExamSessionIncidentConstants.VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamSessionIncidentConstants.INVALID_STATUS);
        }
        if (ExamSessionIncidentConstants.STATUS_CLOSED.equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamSessionIncidentConstants.CLOSED_INCIDENT_IMMUTABLE);
        }
    }

    private void applyProctorTransition(
            ExamSessionIncident incident,
            String previousStatus,
            String newStatus,
            UpdateIncidentStatusRequest request) {
        if (ExamSessionIncidentConstants.STATUS_RESOLVED.equalsIgnoreCase(previousStatus)
                || ExamSessionIncidentConstants.STATUS_ESCALATED.equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    String.format(ExamSessionIncidentConstants.PROCTOR_CANNOT_MODIFY_STATUS, previousStatus));
        }
        if (!ExamSessionIncidentConstants.STATUS_NEW.equalsIgnoreCase(newStatus)
                && !ExamSessionIncidentConstants.STATUS_IN_PROGRESS.equalsIgnoreCase(newStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    ExamSessionIncidentConstants.PROCTOR_STATUS_TRANSITION_FORBIDDEN);
        }
        if (ExamSessionIncidentConstants.STATUS_IN_PROGRESS.equalsIgnoreCase(previousStatus)
                && ExamSessionIncidentConstants.STATUS_NEW.equalsIgnoreCase(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ExamSessionIncidentConstants.PROCTOR_CANNOT_REOPEN);
        }
        if (request.receiverRole() != null && !request.receiverRole().isBlank()
                && !ExamSessionIncidentConstants.OWNER_HOST_ADMIN.equals(request.receiverRole().trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    ExamSessionIncidentConstants.PROCTOR_ROUTE_ONLY_HOST);
        }
        incident.setCurrentOwnerRole(ExamSessionIncidentConstants.OWNER_HOST_ADMIN);
    }

    private void applyHostOrPlatformTransition(
            ExamSessionIncident incident,
            String newStatus,
            UpdateIncidentStatusRequest request) {
        if (ExamSessionIncidentConstants.STATUS_ESCALATED.equalsIgnoreCase(newStatus)) {
            incident.setCurrentOwnerRole(ExamSessionIncidentConstants.OWNER_PLATFORM_ADMIN);
        } else if (request.receiverRole() != null && !request.receiverRole().isBlank()) {
            String receiverRole = request.receiverRole().trim().toUpperCase();
            if (!ExamSessionIncidentConstants.ALLOWED_RECEIVER_ROLES.contains(receiverRole)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        ExamSessionIncidentConstants.INVALID_RECEIVER_ROLE);
            }
            incident.setCurrentOwnerRole(receiverRole);
        } else if (ExamSessionIncidentConstants.STATUS_RESOLVED.equalsIgnoreCase(newStatus)
                || ExamSessionIncidentConstants.STATUS_CLOSED.equalsIgnoreCase(newStatus)) {
            incident.setCurrentOwnerRole(ExamSessionIncidentConstants.OWNER_HOST_ADMIN);
        }
    }

    private ExamSessionIncident findIncident(UUID sessionPublicId, UUID incidentPublicId, UUID tenantId) {
        return incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
                        incidentPublicId, sessionPublicId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ExamSessionIncidentConstants.INCIDENT_NOT_FOUND));
    }

    private void saveHistory(
            ExamSessionIncident incident,
            IncidentAccessContext access,
            String action,
            String fromStatus,
            String toStatus,
            String notes) {
        ExamSessionIncidentHistory history = new ExamSessionIncidentHistory();
        history.setPublicId(UUID.randomUUID());
        history.setIncidentId(incident.getId());
        history.setIncidentPublicId(incident.getPublicId());
        history.setActorPublicId(access.actorPublicId());
        history.setActorName(access.actorName());
        history.setActorRole(access.actorRole());
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setNotes(notes);
        historyRepository.save(history);
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
