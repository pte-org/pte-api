package com.pte.proctoring.internal.mapper;

import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.dto.incident.IncidentView;
import org.springframework.stereotype.Component;

@Component
public class ExamIncidentMapper {

    public IncidentView toView(ExamIncident incident, StudentDisplayNameView student) {
        return toView(incident, student != null ? student.displayName() : null);
    }

    public IncidentView toView(ExamIncident incident, String studentName) {
        return new IncidentView(
                incident.getPublicId(),
                incident.getSessionPublicId(),
                incident.getStudentPublicId(),
                studentName,
                incident.getAttemptPublicId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getType(),
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
}
