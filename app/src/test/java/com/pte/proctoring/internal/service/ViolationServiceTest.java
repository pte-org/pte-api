package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.domain.enums.ViolationType;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ExamIncidentRepository incidentRepository;

    @Mock
    private ProctorAccessPolicy proctorAccessPolicy;

    private final ProctorMapper mapper = new ProctorMapper();
    private ViolationService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID proctorPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptPublicId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

    @BeforeEach
    void setUp() {
        service = new ViolationService(incidentRepository, mapper, proctorAccessPolicy);
    }

    @Test
    void listForSession_returnsTheSessionsProctorFlagsOldestFirst() {
        ExamIncident first = flag(ViolationType.TAB_SWITCH, Instant.parse("2026-09-28T04:00:00Z"));
        ExamIncident second = flag(ViolationType.MULTIPLE_FACES, Instant.parse("2026-09-28T04:00:05Z"));
        when(proctorAccessPolicy.authorizeReadAssigned(sessionPublicId, caller)).thenReturn(tenantId);
        when(incidentRepository.findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
                sessionPublicId, tenantId, IncidentOrigin.PROCTOR_FLAG)).thenReturn(List.of(first, second));

        List<ViolationEventResponse> result = service.listForSession(sessionPublicId, caller);

        assertThat(result).extracting(ViolationEventResponse::violationType)
                .containsExactly(ViolationType.TAB_SWITCH, ViolationType.MULTIPLE_FACES);
    }

    private ExamIncident flag(ViolationType type, Instant detectedAt) {
        ExamIncident incident = new ExamIncident();
        incident.setPublicId(UUID.randomUUID());
        incident.setOrigin(IncidentOrigin.PROCTOR_FLAG);
        incident.setType(type.name());
        incident.setAttemptPublicId(attemptPublicId);
        incident.setReporterPublicId(proctorPublicId);
        incident.setDetectedAt(detectedAt);
        return incident;
    }
}
