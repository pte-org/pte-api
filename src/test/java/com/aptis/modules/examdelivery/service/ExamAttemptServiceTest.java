package com.aptis.modules.examdelivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;
import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;
import com.aptis.modules.examdelivery.dto.SubmitExamRequest;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;
import com.aptis.modules.examdelivery.repository.RetryRequestRepository;
import com.aptis.modules.proctor.service.ProctorStatusPushService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamAttemptService Tests")
class ExamAttemptServiceTest {

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private ExamAttemptAccessGuard accessGuard;

    @Mock
    private ProctorStatusPushService proctorStatusPushService;

    @Mock
    private RetryRequestRepository retryRequestRepository;

    private ExamAttemptService service;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new ExamAttemptService(attemptAnswerRepository, accessGuard, proctorStatusPushService, retryRequestRepository);
        principal = new JwtPrincipal(1L, "STUDENT", "STUDENT_ROLE", 1L);
    }

    @Test
    @DisplayName("submitAttempt: uses loadOwnedAttemptForUpdate (pessimistic lock)")
    void submitAttempt_usesLockedLoadNotUnlockedVariant() {
        // Arrange: The key test is that submitAttempt calls loadOwnedAttemptForUpdate (with lock)
        // not loadOwnedAttempt (without lock), to prevent concurrent submit races per Phase 6 Design Constraint
        Long attemptId = 100L;
        SubmitExamRequest request = new SubmitExamRequest(attemptId);

        when(accessGuard.loadOwnedAttemptForUpdate(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "test"));

        // Act & Assert
        assertThatThrownBy(() -> service.submitAttempt(request, principal))
                .isInstanceOf(ApiException.class);

        // Verify the LOCKED variant was called, not the unlocked one
        verify(accessGuard).loadOwnedAttemptForUpdate(attemptId, principal);
    }

    @Test
    @DisplayName("switchSection: uses unlocked loadOwnedAttempt for read-only check")
    void switchSection_usesUnlockedLoad() {
        Long attemptId = 100L;

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "test"));

        // Act & Assert
        assertThatThrownBy(() -> service.switchSection(attemptId, principal))
                .isInstanceOf(ApiException.class);

        // Verify unlocked variant was called
        verify(accessGuard).loadOwnedAttempt(attemptId, principal);
    }

    @Test
    @DisplayName("recordHeartbeat: delegates to accessGuard.loadOwnedAttempt")
    void recordHeartbeat_usesAccessGuard() {
        Long attemptId = 100L;

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "test"));

        // Act & Assert
        assertThatThrownBy(() -> service.recordHeartbeat(attemptId, principal))
                .isInstanceOf(ApiException.class);

        // Verify guard was called
        verify(accessGuard).loadOwnedAttempt(attemptId, principal);
    }

    @Test
    @DisplayName("recordHeartbeat: throws when attempt not found")
    void recordHeartbeat_throwsWhenNotFound() {
        Long attemptId = 999L;

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Not found"));

        assertThatThrownBy(() -> service.recordHeartbeat(attemptId, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("switchSection: throws when attempt not found")
    void switchSection_throwsWhenNotFound() {
        Long attemptId = 999L;

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Not found"));

        assertThatThrownBy(() -> service.switchSection(attemptId, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("submitAttempt: throws when attempt not found")
    void submitAttempt_throwsWhenNotFound() {
        Long attemptId = 999L;
        SubmitExamRequest request = new SubmitExamRequest(attemptId);

        when(accessGuard.loadOwnedAttemptForUpdate(attemptId, principal))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Not found"));

        assertThatThrownBy(() -> service.submitAttempt(request, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ====================
    // recordHeartbeat + enforceRetryGate() Tests (Phase 9)
    // ====================

    @Test
    @DisplayName("recordHeartbeat: PENDING retry request blocks exam start with ACCESS_DENIED")
    void recordHeartbeat_pendingRetryBlocks() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn("student-123");
        when(attempt.getExamId()).thenReturn(50L);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.NOT_STARTED);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // A PENDING retry request exists for this student+exam
        RetryRequest pendingRequest = RetryRequest.create("student-123", attemptId, 50L);
        when(retryRequestRepository.findMostRecent("student-123", 50L))
                .thenReturn(Optional.of(pendingRequest));

        // Act & Assert
        assertThatThrownBy(() -> service.recordHeartbeat(attemptId, principal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        // Verify repository was queried
        verify(retryRequestRepository).findMostRecent("student-123", 50L);
        // Verify the request was NOT consumed (because it's PENDING)
        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordHeartbeat: APPROVED retry request is consumed and allows heartbeat")
    void recordHeartbeat_approvedRetryIsConsumedAndAllows() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn("student-123");
        when(attempt.getExamId()).thenReturn(50L);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.NOT_STARTED);
        when(attempt.recordHeartbeat()).thenReturn(ExamAttemptStatus.IN_PROGRESS);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // An APPROVED retry request exists for this student+exam
        RetryRequest approvedRequest = RetryRequest.create("student-123", attemptId, 50L);
        approvedRequest.approve(999L); // Approve it
        when(retryRequestRepository.findMostRecent("student-123", 50L))
                .thenReturn(Optional.of(approvedRequest));

        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.recordHeartbeat(attemptId, principal);

        // Assert: the retry request should have been consumed
        verify(retryRequestRepository).save(any(RetryRequest.class));
        // After consuming, status should be CONSUMED
        assertThat(approvedRequest.getStatus()).isEqualTo(RetryRequestStatus.CONSUMED);
    }

    @Test
    @DisplayName("recordHeartbeat: REJECTED retry request allows heartbeat without mutation")
    void recordHeartbeat_rejectedRetryAllowsNoOp() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn("student-123");
        when(attempt.getExamId()).thenReturn(50L);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.NOT_STARTED);
        when(attempt.recordHeartbeat()).thenReturn(ExamAttemptStatus.IN_PROGRESS);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // A REJECTED retry request exists
        RetryRequest rejectedRequest = RetryRequest.create("student-123", attemptId, 50L);
        rejectedRequest.reject(999L);
        when(retryRequestRepository.findMostRecent("student-123", 50L))
                .thenReturn(Optional.of(rejectedRequest));

        // Act
        service.recordHeartbeat(attemptId, principal);

        // Assert: no save should occur for rejected request
        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordHeartbeat: no retry request allows heartbeat without mutation")
    void recordHeartbeat_noRetryRequestAllowsNoOp() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn("student-123");
        when(attempt.getExamId()).thenReturn(50L);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.NOT_STARTED);
        when(attempt.recordHeartbeat()).thenReturn(ExamAttemptStatus.IN_PROGRESS);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // No retry request exists
        when(retryRequestRepository.findMostRecent("student-123", 50L))
                .thenReturn(Optional.empty());

        // Act
        service.recordHeartbeat(attemptId, principal);

        // Assert: no save should occur
        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordHeartbeat: CONSUMED retry request allows heartbeat without additional mutation")
    void recordHeartbeat_consumedRetryAllowsNoOp() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn("student-123");
        when(attempt.getExamId()).thenReturn(50L);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.NOT_STARTED);
        when(attempt.recordHeartbeat()).thenReturn(ExamAttemptStatus.IN_PROGRESS);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // A CONSUMED retry request exists (already used in a prior heartbeat)
        RetryRequest consumedRequest = RetryRequest.create("student-123", attemptId, 50L);
        consumedRequest.approve(999L);
        consumedRequest.consume();
        when(retryRequestRepository.findMostRecent("student-123", 50L))
                .thenReturn(Optional.of(consumedRequest));

        // Act
        service.recordHeartbeat(attemptId, principal);

        // Assert: no additional save should occur
        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordHeartbeat: non-NOT_STARTED attempt skips enforceRetryGate")
    void recordHeartbeat_inProgressSkipsGate() {
        Long attemptId = 100L;
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStatus()).thenReturn(ExamAttemptStatus.IN_PROGRESS);
        when(attempt.recordHeartbeat()).thenReturn(ExamAttemptStatus.IN_PROGRESS);

        when(accessGuard.loadOwnedAttempt(attemptId, principal))
                .thenReturn(attempt);

        // Act
        service.recordHeartbeat(attemptId, principal);

        // Assert: retryRequestRepository should NOT be consulted (gate skipped)
        verify(retryRequestRepository, never()).findMostRecent(any(), any());
    }
}
