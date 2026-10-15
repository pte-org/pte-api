package com.pte.proctoring.internal.mapper;

import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamSessionIncident;
import com.pte.proctoring.domain.ExamSessionIncidentHistory;
import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.dto.incident.IncidentView;
import org.springframework.stereotype.Component;

@Component
public class ExamSessionIncidentMapper {

    public IncidentView toView(ExamSessionIncident incident, StudentDisplayNameView student) {
        return toView(incident, student != null ? student.displayName() : null);
    }

    public IncidentView toView(ExamSessionIncident incident, String studentName) {
        return new IncidentView(
                incident.getPublicId(),
                incident.getSessionPublicId(),
                incident.getStudentPublicId(),
                studentName,
                incident.getAttemptPublicId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getReporterRole(),
                incident.getReporterName(),
                incident.getCurrentOwnerRole(),
                incident.getCurrentOwnerName(),
                incident.getResolutionSummary(),
                incident.getCreatedAt(),
                incident.getUpdatedAt());
    }

    public IncidentHistoryItemView toHistoryView(ExamSessionIncidentHistory history) {
        return new IncidentHistoryItemView(
                history.getPublicId().toString(),
                history.getIncidentPublicId(),
                history.getActorName(),
                history.getActorRole(),
                history.getAction(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getNotes(),
                history.getCreatedAt());
    }
}
