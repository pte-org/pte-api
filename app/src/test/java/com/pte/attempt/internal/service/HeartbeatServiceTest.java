package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatService")
class HeartbeatServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private HeartbeatService heartbeatService;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        heartbeatService = new HeartbeatService(attemptRepository);
        attempt = new ExamAttempt();
        attempt.setId(1L);
    }

    @Test
    @DisplayName("a heartbeat stamps lastSeenAt on the attempt row with a targeted update")
    void recordHeartbeat_stampsLastSeenAt() {
        Instant before = Instant.now();

        heartbeatService.recordHeartbeat(attempt);

        ArgumentCaptor<Instant> seenAt = ArgumentCaptor.forClass(Instant.class);
        verify(attemptRepository).touchLastSeen(eq(1L), seenAt.capture());
        assertThat(seenAt.getValue()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("every heartbeat is its own update, even a repeat for the same attempt")
    void recordHeartbeat_repeatCall_updatesAgain() {
        heartbeatService.recordHeartbeat(attempt);
        heartbeatService.recordHeartbeat(attempt);

        verify(attemptRepository, times(2)).touchLastSeen(eq(1L), org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
