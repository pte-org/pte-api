package com.pte.proctoring.internal.service;

import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExamSessionIncidentService {

    private final ExamSessionIncidentQueryService queryService;
    private final ExamSessionIncidentCommandService commandService;

    public ExamSessionIncidentService(
            ExamSessionIncidentQueryService queryService,
            ExamSessionIncidentCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    public PagedResult<IncidentView> listIncidents(
            UUID sessionPublicId,
            String status,
            int page,
            int size,
            CurrentUser caller) {
        return queryService.listIncidents(sessionPublicId, status, page, size, caller);
    }

    public IncidentView createIncident(
            UUID sessionPublicId,
            CreateIncidentRequest request,
            CurrentUser caller) {
        return commandService.createIncident(sessionPublicId, request, caller);
    }

    public IncidentView getIncident(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {
        return queryService.getIncident(sessionPublicId, incidentPublicId, caller);
    }

    public List<IncidentHistoryItemView> getIncidentHistory(
            UUID sessionPublicId,
            UUID incidentPublicId,
            CurrentUser caller) {
        return queryService.getIncidentHistory(sessionPublicId, incidentPublicId, caller);
    }

    public IncidentView updateIncidentStatus(
            UUID sessionPublicId,
            UUID incidentPublicId,
            UpdateIncidentStatusRequest request,
            CurrentUser caller) {
        return commandService.updateIncidentStatus(sessionPublicId, incidentPublicId, request, caller);
    }
}
