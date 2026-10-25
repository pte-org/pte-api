package com.pte.proctoring.internal.service;

import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentRetentionServiceTest {

    @Mock
    private ExamIncidentRepository incidentRepository;

    @Test
    void marksEvidenceOlderThanConfiguredRetentionWithoutHardDelete() {
        Instant now = Instant.parse("2026-09-28T04:00:00Z");
        when(incidentRepository.markExpiredEvidenceBefore(now.minus(180, ChronoUnit.DAYS))).thenReturn(3);
        IncidentRetentionService service = new IncidentRetentionService(incidentRepository, 180, java.time.Clock.systemUTC());

        int marked = service.markExpired(now);

        assertThat(marked).isEqualTo(3);
        verify(incidentRepository).markExpiredEvidenceBefore(now.minus(180, ChronoUnit.DAYS));
    }

    @Test
    void rejectsANonPositiveRetention() {
        assertThatThrownBy(() -> new IncidentRetentionService(incidentRepository, 0, java.time.Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
