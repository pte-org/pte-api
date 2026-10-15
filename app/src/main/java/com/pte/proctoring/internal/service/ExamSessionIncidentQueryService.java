package com.pte.proctoring.internal.service;

import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamSessionIncident;
import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.internal.constant.ExamSessionIncidentConstants;
import com.pte.proctoring.internal.mapper.ExamSessionIncidentMapper;
import com.pte.proctoring.internal.policy.ExamSessionIncidentAccessPolicy;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.proctoring.internal.repository.ExamSessionIncidentHistoryRepository;
import com.pte.proctoring.internal.repository.ExamSessionIncidentRepository;
import com.pte.shared.security.CurrentUser;
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
import java.util.UUID;

@Service
public class ExamSessionIncidentQueryService {

    private final ExamSessionIncidentRepository incidentRepository;
    private final ExamSessionIncidentHistoryRepository historyRepository;
    private final IdentityService identityService;
    private final ExamSessionIncidentAccessPolicy accessPolicy;
    private final ExamSessionIncidentMapper mapper;

    public ExamSessionIncidentQueryService(
            ExamSessionIncidentRepository incidentRepository,
            ExamSessionIncidentHistoryRepository historyRepository,
            IdentityService identityService,
            ExamSessionIncidentAccessPolicy accessPolicy,
            ExamSessionIncidentMapper mapper) {
        this.incidentRepository = incidentRepository;
        this.historyRepository = historyRepository;
        this.identityService = identityService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PagedResult<IncidentView> listIncidents(
            UUID sessionPublicId,
            String status,
            int page,
            int size,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExamSessionIncident> incidentPage;

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            incidentPage = incidentRepository.findBySessionPublicIdAndTenantIdAndStatusAndDeletedFalse(
                    sessionPublicId, access.tenantId(), status.trim().toUpperCase(), pageRequest);
        } else {
            incidentPage = incidentRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(
                    sessionPublicId, access.tenantId(), pageRequest);
        }

        List<UUID> studentIds = incidentPage.getContent().stream()
                .map(ExamSessionIncident::getStudentPublicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, StudentDisplayNameView> nameMap = resolveStudentNames(access.tenantId(), studentIds);
        List<IncidentView> views = incidentPage.getContent().stream()
                .map(incident -> mapper.toView(incident, nameMap.get(incident.getStudentPublicId())))
                .toList();

        PageMeta meta = new PageMeta(
                incidentPage.getNumber(),
                incidentPage.getSize(),
                incidentPage.getTotalElements(),
                incidentPage.getTotalPages(),
                incidentPage.isFirst(),
                incidentPage.isLast(),
                incidentPage.hasNext(),
                incidentPage.hasPrevious());
        return new PagedResult<>(views, meta);
    }

    @Transactional(readOnly = true)
    public IncidentView getIncident(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        ExamSessionIncident incident = findIncident(sessionPublicId, incidentPublicId, access.tenantId());
        StudentDisplayNameView student = resolveStudentName(access.tenantId(), incident.getStudentPublicId());
        return mapper.toView(incident, student);
    }

    @Transactional(readOnly = true)
    public List<IncidentHistoryItemView> getIncidentHistory(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {
        IncidentAccessContext access = accessPolicy.authorize(sessionPublicId, caller);
        findIncident(sessionPublicId, incidentPublicId, access.tenantId());
        return historyRepository.findByIncidentPublicIdAndDeletedFalseOrderByCreatedAtAsc(incidentPublicId)
                .stream()
                .map(mapper::toHistoryView)
                .toList();
    }

    private ExamSessionIncident findIncident(UUID sessionPublicId, UUID incidentPublicId, UUID tenantId) {
        return incidentRepository.findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
                        incidentPublicId, sessionPublicId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ExamSessionIncidentConstants.INCIDENT_NOT_FOUND));
    }

    private Map<UUID, StudentDisplayNameView> resolveStudentNames(UUID tenantId, List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<UUID, StudentDisplayNameView> names = identityService.getStudentDisplayNames(tenantId, studentIds);
            return names != null ? names : Collections.emptyMap();
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private StudentDisplayNameView resolveStudentName(UUID tenantId, UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return resolveStudentNames(tenantId, List.of(studentId)).get(studentId);
    }
}
