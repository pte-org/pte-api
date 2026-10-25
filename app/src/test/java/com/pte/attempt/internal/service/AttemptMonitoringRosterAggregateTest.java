package com.pte.attempt.internal.service;

import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.dto.response.MonitoringRosterAggregateView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptMonitoringRosterAggregateTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private AttemptMonitoringQueryService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttemptMonitoringQueryService(attemptRepository);
    }

    private static ExamAttemptRepository.LatestStatusRow row(UUID student, AttemptStatus status) {
        return new ExamAttemptRepository.LatestStatusRow() {
            @Override
            public UUID getStudentPublicId() {
                return student;
            }

            @Override
            public AttemptStatus getStatus() {
                return status;
            }
        };
    }

    @Test
    void countsEachEnrolledStudentOnceByLatestStatusAndTheRestAsNotStarted() {
        UUID inProgress = UUID.randomUUID();
        UUID submitted = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        List<UUID> enrolled = List.of(inProgress, submitted, created, idle);
        when(attemptRepository.findLatestStatuses(eq(sessionId), eq(tenantId), any())).thenReturn(List.of(
                row(inProgress, AttemptStatus.IN_PROGRESS), row(submitted, AttemptStatus.SUBMITTED),
                row(created, AttemptStatus.CREATED)));

        MonitoringRosterAggregateView view = service.getRosterAggregate(sessionId, tenantId, enrolled);

        assertThat(view).isEqualTo(new MonitoringRosterAggregateView(4, 1, 1, 1, 1));
    }

    @Test
    void duplicateAndNullStudentIdsAreCountedOnce() {
        UUID student = UUID.randomUUID();
        List<UUID> enrolled = new ArrayList<>(List.of(student, student));
        enrolled.add(null);
        when(attemptRepository.findLatestStatuses(eq(sessionId), eq(tenantId), any())).thenReturn(List.of());

        assertThat(service.getRosterAggregate(sessionId, tenantId, enrolled))
                .isEqualTo(new MonitoringRosterAggregateView(1, 1, 0, 0, 0));
    }

    @Test
    void noEnrolledStudentsMeansAnEmptyAggregateWithoutQuerying() {
        assertThat(service.getRosterAggregate(sessionId, tenantId, List.of()))
                .isEqualTo(new MonitoringRosterAggregateView(0, 0, 0, 0, 0));
        verify(attemptRepository, times(0)).findLatestStatuses(any(), any(), any());
    }

    @Test
    void largeRostersAreQueriedInBatches() {
        List<UUID> enrolled = IntStream.range(0, 1200).mapToObj(index -> UUID.randomUUID()).toList();
        when(attemptRepository.findLatestStatuses(eq(sessionId), eq(tenantId), any())).thenReturn(List.of());

        MonitoringRosterAggregateView view = service.getRosterAggregate(sessionId, tenantId, enrolled);

        assertThat(view.registeredCount()).isEqualTo(1200);
        verify(attemptRepository, times(3)).findLatestStatuses(any(), any(), any());
    }
}
