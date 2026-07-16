package com.aptis.modules.examdelivery.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examoperations.domain.Exam;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamAttemptAccessGuard Tests")
class ExamAttemptAccessGuardTest {

    @Mock
    private ExamAttemptRepository repository;

    private ExamAttemptAccessGuard guard;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        guard = new ExamAttemptAccessGuard(repository);
        principal = new JwtPrincipal(1L, "STUDENT", "STUDENT_ROLE", 1L);
    }

    @Test
    @DisplayName("loadOwnedAttempt: throws 404 when attempt not found (not 403)")
    void loadOwnedAttempt_throw404NotFoundNotForbidden() {
        Long attemptId = 999L;
        when(repository.findById(attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.loadOwnedAttempt(attemptId, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("loadOwnedAttemptForUpdate: throws 404 when not found (uses pessimistic lock)")
    void loadOwnedAttemptForUpdate_throw404WhenNotFound() {
        Long attemptId = 999L;
        when(repository.findByIdForUpdate(attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.loadOwnedAttemptForUpdate(attemptId, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes when both start and end are null")
    void checkAvailabilityWindow_passWhenBothNull() {
        Exam exam = mock(Exam.class);
        when(exam.getAvailabilityStart()).thenReturn(null);
        when(exam.getAvailabilityEnd()).thenReturn(null);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes when now is within [start, end]")
    void checkAvailabilityWindow_passWhenWithinWindow() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(1);
        OffsetDateTime end = now.plusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(start);
        when(exam.getAvailabilityEnd()).thenReturn(end);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: throws 403 when now is before start")
    void checkAvailabilityWindow_throw403WhenBeforeStart() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.plusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(start);

        assertThatThrownBy(() -> guard.checkAvailabilityWindow(exam))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: throws 403 when now is after end")
    void checkAvailabilityWindow_throw403WhenAfterEnd() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime end = now.minusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(null);
        when(exam.getAvailabilityEnd()).thenReturn(end);

        assertThatThrownBy(() -> guard.checkAvailabilityWindow(exam))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes when only start is null")
    void checkAvailabilityWindow_passWhenOnlyStartNull() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime end = now.plusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(null);
        when(exam.getAvailabilityEnd()).thenReturn(end);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes when only end is null")
    void checkAvailabilityWindow_passWhenOnlyEndNull() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(start);
        when(exam.getAvailabilityEnd()).thenReturn(null);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes at exact start time (boundary)")
    void checkAvailabilityWindow_passAtExactStartTime() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now;
        OffsetDateTime end = now.plusHours(1);

        when(exam.getAvailabilityStart()).thenReturn(start);
        when(exam.getAvailabilityEnd()).thenReturn(end);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }

    @Test
    @DisplayName("checkAvailabilityWindow: passes with generous window")
    void checkAvailabilityWindow_passWithGenerousWindow() {
        Exam exam = mock(Exam.class);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = now.minusHours(2);
        OffsetDateTime end = now.plusHours(2);

        when(exam.getAvailabilityStart()).thenReturn(start);
        when(exam.getAvailabilityEnd()).thenReturn(end);

        // Should not throw
        guard.checkAvailabilityWindow(exam);
    }
}
