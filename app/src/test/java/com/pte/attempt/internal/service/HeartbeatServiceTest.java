package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptHeartbeat;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.repository.AttemptHeartbeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ported from services/exam-delivery's own HeartbeatServiceTest — same coverage, new package. */
@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatService")
class HeartbeatServiceTest {

    @Mock
    private AttemptHeartbeatRepository heartbeatRepository;

    private HeartbeatService heartbeatService;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        heartbeatService = new HeartbeatService(heartbeatRepository);
        attempt = new ExamAttempt();
        attempt.setId(1L);
    }

    @Test
    @DisplayName("first heartbeat for an attempt creates a new row")
    void recordHeartbeat_firstCall_createsNewRow() {
        when(heartbeatRepository.findByAttemptId(1L)).thenReturn(Optional.empty());
        when(heartbeatRepository.save(any(AttemptHeartbeat.class))).thenAnswer(inv -> inv.getArgument(0));

        heartbeatService.recordHeartbeat(attempt);

        ArgumentCaptor<AttemptHeartbeat> captor = ArgumentCaptor.forClass(AttemptHeartbeat.class);
        verify(heartbeatRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isSameAs(attempt);
    }

    @Test
    @DisplayName("second heartbeat for the same attempt reuses (upserts) the existing row, not a new one")
    void recordHeartbeat_secondCall_reusesExistingRow() {
        AttemptHeartbeat existing = new AttemptHeartbeat();
        existing.setId(99L);
        when(heartbeatRepository.findByAttemptId(1L)).thenReturn(Optional.of(existing));
        when(heartbeatRepository.save(any(AttemptHeartbeat.class))).thenAnswer(inv -> inv.getArgument(0));

        heartbeatService.recordHeartbeat(attempt);

        ArgumentCaptor<AttemptHeartbeat> captor = ArgumentCaptor.forClass(AttemptHeartbeat.class);
        verify(heartbeatRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(99L);
        assertThat(captor.getValue().getAttempt()).isSameAs(attempt);
    }

    @Test
    @DisplayName("lastSeenAt advances on every call, even a repeat call for the same already-associated attempt")
    void recordHeartbeat_repeatCallForSameAttempt_lastSeenAtAdvances() {
        AttemptHeartbeat existing = new AttemptHeartbeat();
        existing.setId(99L);
        existing.setAttempt(attempt);
        Instant firstSeen = Instant.now().minusSeconds(15);
        existing.setLastSeenAt(firstSeen);
        when(heartbeatRepository.findByAttemptId(1L)).thenReturn(Optional.of(existing));
        when(heartbeatRepository.save(any(AttemptHeartbeat.class))).thenAnswer(inv -> inv.getArgument(0));

        heartbeatService.recordHeartbeat(attempt);

        ArgumentCaptor<AttemptHeartbeat> captor = ArgumentCaptor.forClass(AttemptHeartbeat.class);
        verify(heartbeatRepository).save(captor.capture());
        assertThat(captor.getValue().getLastSeenAt()).isAfter(firstSeen);
    }

    @Test
    @DisplayName("a first-insert race (DataIntegrityViolationException) is swallowed, not thrown to the caller")
    void recordHeartbeat_concurrentFirstInsertRace_doesNotThrow() {
        when(heartbeatRepository.findByAttemptId(1L)).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(heartbeatRepository).save(any(AttemptHeartbeat.class));

        assertThatCode(() -> heartbeatService.recordHeartbeat(attempt)).doesNotThrowAnyException();
    }
}
