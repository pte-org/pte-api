package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamSessionIncident;
import com.pte.proctoring.domain.ExamSessionIncidentHistory;
import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.proctoring.internal.repository.ExamSessionIncidentHistoryRepository;
import com.pte.proctoring.internal.repository.ExamSessionIncidentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ExamSessionIncidentService {

    private final ExamSessionIncidentRepository incidentRepository;
    private final ExamSessionIncidentHistoryRepository historyRepository;
    private final IdentityService identityService;
    private final SessionService sessionService;
    private final AttemptService attemptService;

    public ExamSessionIncidentService(
            ExamSessionIncidentRepository incidentRepository,
            ExamSessionIncidentHistoryRepository historyRepository,
            IdentityService identityService,
            SessionService sessionService,
            AttemptService attemptService) {
        this.incidentRepository = incidentRepository;
        this.historyRepository = historyRepository;
        this.identityService = identityService;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
    }

    private void verifyAccess(UUID sessionPublicId, CurrentUser caller) {
        if (caller != null && caller.hasRole(SecurityRoles.PROCTOR)) {
            boolean assigned = sessionService.isProctorAssignedToSession(sessionPublicId, caller.userId(), caller.tenantId());
            if (!assigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proctor is not assigned to this exam session");
            }
        }
    }

    private String resolveActorRole(CurrentUser caller) {
        if (caller == null) {
            return "UNKNOWN";
        }
        if (caller.hasRole(SecurityRoles.PROCTOR)) {
            return SecurityRoles.PROCTOR;
        }
        if (caller.hasRole(SecurityRoles.HOST_ADMIN)) {
            return SecurityRoles.HOST_ADMIN;
        }
        return (caller.roles() != null && !caller.roles().isEmpty()) ? caller.roles().get(0) : "UNKNOWN";
    }

    private String resolveActorName(CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            return "SYSTEM";
        }
        return caller.userId().toString();
    }

    @Transactional(readOnly = true)
    public PagedResult<IncidentView> listIncidents(
            UUID sessionPublicId,
            String status,
            int page,
            int size,
            CurrentUser caller) {

        verifyAccess(sessionPublicId, caller);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExamSessionIncident> incidentPage;

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            incidentPage = incidentRepository.findBySessionPublicIdAndTenantIdAndStatusAndDeletedFalse(
                    sessionPublicId, caller.tenantId(), status.trim().toUpperCase(), pageRequest);
        } else {
            incidentPage = incidentRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(
                    sessionPublicId, caller.tenantId(), pageRequest);
        }

        List<UUID> studentIds = incidentPage.getContent().stream()
                .map(ExamSessionIncident::getStudentPublicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, StudentDisplayNameView> nameMap = resolveStudentNames(caller.tenantId(), studentIds);

        List<IncidentView> views = incidentPage.getContent().stream()
                .map(inc -> toIncidentView(inc, nameMap.get(inc.getStudentPublicId())))
                .toList();

        PageMeta meta = new PageMeta(
                incidentPage.getNumber(),
                incidentPage.getSize(),
                incidentPage.getTotalElements(),
                incidentPage.getTotalPages(),
                incidentPage.isFirst(),
                incidentPage.isLast(),
                incidentPage.hasNext(),
                incidentPage.hasPrevious()
        );

        return new PagedResult<>(views, meta);
    }

    @Transactional
    public IncidentView createIncident(
            UUID sessionPublicId,
            CreateIncidentRequest request,
            CurrentUser caller) {

        verifyAccess(sessionPublicId, caller);

        if (request.studentPublicId() != null) {
            boolean enrolled = sessionService.isStudentEnrolledInSession(sessionPublicId, request.studentPublicId(), caller.tenantId());
            if (!enrolled) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is not enrolled in this exam session");
            }
        }

        if (request.attemptPublicId() != null) {
            boolean validAttempt = attemptService.attemptBelongsToSessionAndStudent(
                    request.attemptPublicId(), sessionPublicId, request.studentPublicId(), caller.tenantId());
            if (!validAttempt) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempt does not belong to this exam session or student");
            }
        }

        String actorRole = resolveActorRole(caller);
        String actorName = resolveActorName(caller);

        ExamSessionIncident incident = new ExamSessionIncident();
        incident.setPublicId(UUID.randomUUID());
        incident.setTenantId(caller.tenantId());
        incident.setSessionPublicId(sessionPublicId);
        incident.setStudentPublicId(request.studentPublicId());
        incident.setAttemptPublicId(request.attemptPublicId());
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description().trim());
        incident.setCategory(request.category().trim().toUpperCase());
        incident.setSeverity(request.severity().trim().toUpperCase());
        incident.setStatus("NEW");
        incident.setReporterRole(actorRole);
        incident.setReporterName(actorName);
        incident.setReporterPublicId(caller.userId());
        incident.setCurrentOwnerRole("HOST_ADMIN");
        incident.setCurrentOwnerName(null);
        incident.setResolutionSummary(null);

        ExamSessionIncident saved = incidentRepository.save(incident);

        ExamSessionIncidentHistory history = new ExamSessionIncidentHistory();
        history.setPublicId(UUID.randomUUID());
        history.setIncidentId(saved.getId());
        history.setIncidentPublicId(saved.getPublicId());
        history.setActorPublicId(caller.userId());
        history.setActorName(actorName);
        history.setActorRole(actorRole);
        history.setAction("CREATE");
        history.setFromStatus(null);
        history.setToStatus("NEW");
        history.setNotes(request.description());

        historyRepository.save(history);

        String studentName = null;
        if (saved.getStudentPublicId() != null) {
            Map<UUID, StudentDisplayNameView> names = resolveStudentNames(caller.tenantId(), List.of(saved.getStudentPublicId()));
            if (names.containsKey(saved.getStudentPublicId())) {
                studentName = names.get(saved.getStudentPublicId()).displayName();
            }
        }

        return toIncidentView(saved, studentName);
    }

    @Transactional(readOnly = true)
    public IncidentView getIncident(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {

        verifyAccess(sessionPublicId, caller);

        ExamSessionIncident incident = incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
                        incidentPublicId, sessionPublicId, caller.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));

        String studentName = null;
        if (incident.getStudentPublicId() != null) {
            Map<UUID, StudentDisplayNameView> names = resolveStudentNames(caller.tenantId(), List.of(incident.getStudentPublicId()));
            if (names.containsKey(incident.getStudentPublicId())) {
                studentName = names.get(incident.getStudentPublicId()).displayName();
            }
        }

        return toIncidentView(incident, studentName);
    }

    @Transactional(readOnly = true)
    public List<IncidentHistoryItemView> getIncidentHistory(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {

        verifyAccess(sessionPublicId, caller);

        // Validate incident exists in scope
        incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
                        incidentPublicId, sessionPublicId, caller.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));

        return historyRepository.findByIncidentPublicIdAndDeletedFalseOrderByCreatedAtAsc(incidentPublicId)
                .stream()
                .map(h -> new IncidentHistoryItemView(
                        h.getPublicId().toString(),
                        h.getIncidentPublicId(),
                        h.getActorName(),
                        h.getActorRole(),
                        h.getAction(),
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getNotes(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public IncidentView updateIncidentStatus(
            UUID sessionPublicId,
            UUID incidentPublicId,
            UpdateIncidentStatusRequest request,
            CurrentUser caller) {

        verifyAccess(sessionPublicId, caller);

        ExamSessionIncident incident = incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
                        incidentPublicId, sessionPublicId, caller.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));

        String previousStatus = incident.getStatus();
        String newStatus = request.status().trim().toUpperCase();

        Set<String> validStatuses = Set.of("NEW", "IN_PROGRESS", "ESCALATED", "RESOLVED", "CLOSED");
        if (!validStatuses.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be one of NEW, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED");
        }

        if ("CLOSED".equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closed incidents cannot be modified");
        }

        boolean isProctor = caller != null && caller.hasRole(SecurityRoles.PROCTOR);
        if (isProctor) {
            if ("RESOLVED".equalsIgnoreCase(previousStatus) || "ESCALATED".equalsIgnoreCase(previousStatus)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proctor cannot modify incidents that are " + previousStatus);
            }
            if (!"NEW".equalsIgnoreCase(newStatus) && !"IN_PROGRESS".equalsIgnoreCase(newStatus)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proctor is not authorized to escalate, resolve or close incidents");
            }
            if ("IN_PROGRESS".equalsIgnoreCase(previousStatus) && "NEW".equalsIgnoreCase(newStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transition incident from IN_PROGRESS back to NEW");
            }
            if (request.receiverRole() != null && !request.receiverRole().isBlank()) {
                String reqRole = request.receiverRole().trim().toUpperCase();
                if (!"HOST_ADMIN".equals(reqRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proctor can only route incidents to HOST_ADMIN");
                }
            }
            incident.setCurrentOwnerRole("HOST_ADMIN");
        } else {
            Set<String> allowedReceiverRoles = Set.of("HOST_ADMIN", "PLATFORM_ADMIN", "PROCTOR");

            if ("ESCALATED".equalsIgnoreCase(newStatus)) {
                incident.setCurrentOwnerRole("PLATFORM_ADMIN");
            } else if (request.receiverRole() != null && !request.receiverRole().isBlank()) {
                String reqRole = request.receiverRole().trim().toUpperCase();
                if (!allowedReceiverRoles.contains(reqRole)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid receiver role. Must be one of: HOST_ADMIN, PLATFORM_ADMIN, PROCTOR");
                }
                incident.setCurrentOwnerRole(reqRole);
            } else if ("RESOLVED".equalsIgnoreCase(newStatus) || "CLOSED".equalsIgnoreCase(newStatus)) {
                incident.setCurrentOwnerRole("HOST_ADMIN");
            }
        }

        incident.setStatus(newStatus);
        if (request.resolutionSummary() != null) {
            incident.setResolutionSummary(request.resolutionSummary().trim());
        }

        ExamSessionIncident saved = incidentRepository.save(incident);

        String actorRole = resolveActorRole(caller);
        String actorName = resolveActorName(caller);

        ExamSessionIncidentHistory history = new ExamSessionIncidentHistory();
        history.setPublicId(UUID.randomUUID());
        history.setIncidentId(saved.getId());
        history.setIncidentPublicId(saved.getPublicId());
        history.setActorPublicId(caller.userId());
        history.setActorName(actorName);
        history.setActorRole(actorRole);
        history.setAction("UPDATE_STATUS");
        history.setFromStatus(previousStatus);
        history.setToStatus(newStatus);
        history.setNotes(request.notes());

        historyRepository.save(history);

        String studentName = null;
        if (saved.getStudentPublicId() != null) {
            Map<UUID, StudentDisplayNameView> names = resolveStudentNames(caller.tenantId(), List.of(saved.getStudentPublicId()));
            if (names.containsKey(saved.getStudentPublicId())) {
                studentName = names.get(saved.getStudentPublicId()).displayName();
            }
        }

        return toIncidentView(saved, studentName);
    }

    private Map<UUID, StudentDisplayNameView> resolveStudentNames(UUID tenantId, List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<UUID, StudentDisplayNameView> names = identityService.getStudentDisplayNames(tenantId, studentIds);
            return names != null ? names : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private IncidentView toIncidentView(ExamSessionIncident inc, StudentDisplayNameView studentView) {
        String studentName = studentView != null ? studentView.displayName() : null;
        return toIncidentView(inc, studentName);
    }

    private IncidentView toIncidentView(ExamSessionIncident inc, String studentName) {
        return new IncidentView(
                inc.getPublicId(),
                inc.getSessionPublicId(),
                inc.getStudentPublicId(),
                studentName,
                inc.getAttemptPublicId(),
                inc.getTitle(),
                inc.getDescription(),
                inc.getCategory(),
                inc.getSeverity(),
                inc.getStatus(),
                inc.getReporterRole(),
                inc.getReporterName(),
                inc.getCurrentOwnerRole(),
                inc.getCurrentOwnerName(),
                inc.getResolutionSummary(),
                inc.getCreatedAt(),
                inc.getUpdatedAt()
        );
    }
}
