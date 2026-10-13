package com.pte.session.internal.service.lifecycle;

import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAutoOpenWorkerTest {

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private SessionStateService stateService;

    @Test
    void openDueSessions_rechecksEachCandidateThroughStateService() {
        Instant now = Instant.parse("2026-10-13T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(sessionRepository.findDueForAutoOpen(now)).thenReturn(List.of(first, second));
        when(stateService.openIfDue(first, now)).thenReturn(Optional.of(mock(SessionResponse.class)));
        when(stateService.openIfDue(second, now)).thenReturn(Optional.empty());

        int opened = new SessionAutoOpenWorker(sessionRepository, stateService, clock).openDueSessions();

        assertThat(opened).isEqualTo(1);
    }
}
