package com.aptis.modules.examdelivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.aptis.modules.examdelivery.dto.SubmitExamRequest;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;
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

    private ExamAttemptService service;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new ExamAttemptService(attemptAnswerRepository, accessGuard, proctorStatusPushService);
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
}
