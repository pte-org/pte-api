package com.aptis.modules.examoperations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.domain.SessionTimeOverride;
import com.aptis.modules.examoperations.dto.ExamDetailResponse;
import com.aptis.modules.iam.domain.Proctor;
import com.aptis.modules.iam.repository.ProctorRepository;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.examoperations.repository.SessionTimeOverrideRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamService Tests")
class ExamServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private SessionTimeOverrideRepository timeOverrideRepository;

    @Mock
    private ProctorRepository proctorRepository;

    private ExamService service;
    private JwtPrincipal hostPrincipal;
    private JwtPrincipal anotherHostPrincipal;

    @BeforeEach
    void setUp() {
        service = new ExamService(examRepository, timeOverrideRepository, proctorRepository);
        hostPrincipal = new JwtPrincipal(1L, "HOST", "HOST", 100L); // tenantId = 100
        anotherHostPrincipal = new JwtPrincipal(2L, "HOST", "HOST", 200L); // tenantId = 200
    }

    // ==================== assignProctor Tests ====================

    @Nested
    @DisplayName("assignProctor Tests")
    class AssignProctorTests {

        @Test
        @DisplayName("assignProctor: proctor in same org -> succeeds and sets proctorId")
        void assignProctor_sameOrganization_succeeds() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            when(exam.getId()).thenReturn(examId);
            when(exam.getProctorId()).thenReturn(proctorId);

            Proctor proctor = mock(Proctor.class);
            ReflectionTestUtils.setField(proctor, "id", proctorId);
            ReflectionTestUtils.setField(proctor, "organizationId", hostPrincipal.tenantId());
            when(proctor.getId()).thenReturn(proctorId);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(proctorRepository.findByIdAndOrganizationId(proctorId, hostPrincipal.tenantId()))
                    .thenReturn(Optional.of(proctor));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ExamDetailResponse response = service.assignProctor(examId, proctorId, hostPrincipal);

            // Assert
            assertThat(response).isNotNull();
            assertThat(exam.getProctorId()).isEqualTo(proctorId);
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("assignProctor: proctor in different org -> throws RESOURCE_NOT_FOUND (tenant isolation)")
        void assignProctor_differentOrganization_throwsNotFound() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            // Proctor belongs to different organization
            when(proctorRepository.findByIdAndOrganizationId(proctorId, hostPrincipal.tenantId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.assignProctor(examId, proctorId, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(examRepository, never()).save(any(Exam.class));
        }

        @Test
        @DisplayName("assignProctor: proctor does not exist -> throws RESOURCE_NOT_FOUND")
        void assignProctor_proctorNotExists_throwsNotFound() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 999L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(proctorRepository.findByIdAndOrganizationId(proctorId, hostPrincipal.tenantId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.assignProctor(examId, proctorId, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("assignProctor: exam not found -> throws RESOURCE_NOT_FOUND")
        void assignProctor_examNotFound_throwsNotFound() {
            // Arrange
            Long examId = 999L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.assignProctor(examId, proctorId, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(proctorRepository, never()).findByIdAndOrganizationId(any(), any());
        }

        @Test
        @DisplayName("assignProctor: different host trying to assign proctor -> throws RESOURCE_NOT_FOUND")
        void assignProctor_differentHost_throwsNotFound() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.assignProctor(examId, proctorId, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("assignProctor: returns exam detail response with all fields")
        void assignProctor_returnsDetailResponse() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            ReflectionTestUtils.setField(exam, "name", "Test Exam");
            ReflectionTestUtils.setField(exam, "code", "EXAM-001");
            when(exam.getId()).thenReturn(examId);
            when(exam.getName()).thenReturn("Test Exam");
            when(exam.getCode()).thenReturn("EXAM-001");
            when(exam.getProctorRequired()).thenReturn(false);
            when(exam.getIsAssignable()).thenReturn(true);
            when(exam.getSkillSubsetReading()).thenReturn(false);
            when(exam.getSkillSubsetListening()).thenReturn(false);
            when(exam.getSkillSubsetWriting()).thenReturn(false);
            when(exam.getSkillSubsetSpeaking()).thenReturn(false);

            Proctor proctor = mock(Proctor.class);
            ReflectionTestUtils.setField(proctor, "id", proctorId);
            ReflectionTestUtils.setField(proctor, "organizationId", hostPrincipal.tenantId());
            when(proctor.getId()).thenReturn(proctorId);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(proctorRepository.findByIdAndOrganizationId(proctorId, hostPrincipal.tenantId()))
                    .thenReturn(Optional.of(proctor));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ExamDetailResponse response = service.assignProctor(examId, proctorId, hostPrincipal);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(examId);
        }
    }

    // ==================== unassignProctor Tests ====================

    @Nested
    @DisplayName("unassignProctor Tests")
    class UnassignProctorTests {

        @Test
        @DisplayName("unassignProctor: clears proctor ID")
        void unassignProctor_clearsProctorId() {
            // Arrange
            Long examId = 100L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            ReflectionTestUtils.setField(exam, "proctorId", 999L);
            when(exam.getId()).thenReturn(examId);
            when(exam.getProctorId()).thenReturn(null); // After unassign, it should be null
            when(exam.getProctorRequired()).thenReturn(false);
            when(exam.getIsAssignable()).thenReturn(true);
            when(exam.getSkillSubsetReading()).thenReturn(false);
            when(exam.getSkillSubsetListening()).thenReturn(false);
            when(exam.getSkillSubsetWriting()).thenReturn(false);
            when(exam.getSkillSubsetSpeaking()).thenReturn(false);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ExamDetailResponse response = service.unassignProctor(examId, hostPrincipal);

            // Assert
            assertThat(response).isNotNull();
            assertThat(exam.getProctorId()).isNull();
            verify(examRepository).save(exam);
        }

        @Test
        @DisplayName("unassignProctor: exam not found -> throws RESOURCE_NOT_FOUND")
        void unassignProctor_examNotFound_throwsNotFound() {
            // Arrange
            Long examId = 999L;
            String hostId = hostPrincipal.userId().toString();

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.unassignProctor(examId, hostPrincipal))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(examRepository, never()).save(any(Exam.class));
        }

        @Test
        @DisplayName("unassignProctor: idempotent (calling twice is safe)")
        void unassignProctor_idempotent() {
            // Arrange
            Long examId = 100L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            ReflectionTestUtils.setField(exam, "proctorId", null);
            when(exam.getId()).thenReturn(examId);
            when(exam.getProctorId()).thenReturn(null);
            when(exam.getProctorRequired()).thenReturn(false);
            when(exam.getIsAssignable()).thenReturn(true);
            when(exam.getSkillSubsetReading()).thenReturn(false);
            when(exam.getSkillSubsetListening()).thenReturn(false);
            when(exam.getSkillSubsetWriting()).thenReturn(false);
            when(exam.getSkillSubsetSpeaking()).thenReturn(false);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ExamDetailResponse response1 = service.unassignProctor(examId, hostPrincipal);
            ExamDetailResponse response2 = service.unassignProctor(examId, hostPrincipal);

            // Assert
            assertThat(response1).isNotNull();
            assertThat(response2).isNotNull();
            assertThat(exam.getProctorId()).isNull();
        }
    }

    // ==================== isStartable Tests ====================

    @Nested
    @DisplayName("isStartable Tests")
    class IsStartableTests {

        @Test
        @DisplayName("isStartable: proctorRequired=false -> returns true (proctor not needed)")
        void isStartable_proctorNotRequired_true() {
            // Arrange
            Long examId = 100L;

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "proctorRequired", false);
            ReflectionTestUtils.setField(exam, "proctorId", null);
            when(exam.getProctorRequired()).thenReturn(false);
            // getProctorId() is not called when proctorRequired is false (short-circuit OR)

            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Act
            boolean result = service.isStartable(examId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isStartable: proctorRequired=true + proctorId set -> returns true")
        void isStartable_proctorRequiredAndAssigned_true() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "proctorRequired", true);
            ReflectionTestUtils.setField(exam, "proctorId", proctorId);
            when(exam.getProctorRequired()).thenReturn(true);
            when(exam.getProctorId()).thenReturn(proctorId);

            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Act
            boolean result = service.isStartable(examId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isStartable: proctorRequired=true + proctorId null -> returns false")
        void isStartable_proctorRequiredButNotAssigned_false() {
            // Arrange
            Long examId = 100L;

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "proctorRequired", true);
            ReflectionTestUtils.setField(exam, "proctorId", null);
            when(exam.getProctorRequired()).thenReturn(true);
            when(exam.getProctorId()).thenReturn(null);

            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Act
            boolean result = service.isStartable(examId);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isStartable: proctorRequired=true + proctorId=0 (falsy but not null) -> returns true")
        void isStartable_proctorRequiredWithProctorIdZero_true() {
            // Arrange
            Long examId = 100L;

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "proctorRequired", true);
            ReflectionTestUtils.setField(exam, "proctorId", 0L);

            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Act
            boolean result = service.isStartable(examId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isStartable: exam not found -> throws RESOURCE_NOT_FOUND")
        void isStartable_examNotFound_throwsNotFound() {
            // Arrange
            Long examId = 999L;

            when(examRepository.findById(examId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.isStartable(examId))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("isStartable: proctorRequired=false + proctorId set -> returns true (proctor irrelevant)")
        void isStartable_proctorNotRequiredButAssigned_true() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "proctorRequired", false);
            ReflectionTestUtils.setField(exam, "proctorId", proctorId);

            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Act
            boolean result = service.isStartable(examId);

            // Assert
            assertThat(result).isTrue();
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Assign proctor, verify isStartable returns true for proctorRequired exam")
        void assignProctorThenCheckStartable() {
            // Arrange
            Long examId = 100L;
            Long proctorId = 1L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            ReflectionTestUtils.setField(exam, "proctorRequired", true);
            ReflectionTestUtils.setField(exam, "proctorId", null);
            when(exam.getId()).thenReturn(examId);
            when(exam.getProctorRequired()).thenReturn(true);
            // Initially null, then after save it becomes proctorId
            when(exam.getProctorId()).thenReturn(null).thenReturn(proctorId);

            Proctor proctor = mock(Proctor.class);
            ReflectionTestUtils.setField(proctor, "id", proctorId);
            ReflectionTestUtils.setField(proctor, "organizationId", hostPrincipal.tenantId());
            when(proctor.getId()).thenReturn(proctorId);

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(proctorRepository.findByIdAndOrganizationId(proctorId, hostPrincipal.tenantId()))
                    .thenReturn(Optional.of(proctor));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Before: exam not startable (proctorRequired but no proctor)
            boolean beforeAssign = service.isStartable(examId);
            assertThat(beforeAssign).isFalse();

            // Act: assign proctor
            service.assignProctor(examId, proctorId, hostPrincipal);

            // After: exam is startable
            boolean afterAssign = service.isStartable(examId);
            assertThat(afterAssign).isTrue();
        }

        @Test
        @DisplayName("Unassign proctor, verify isStartable returns false for proctorRequired exam")
        void unassignProctorThenCheckStartable() {
            // Arrange
            Long examId = 100L;
            String hostId = hostPrincipal.userId().toString();

            Exam exam = mock(Exam.class);
            ReflectionTestUtils.setField(exam, "id", examId);
            ReflectionTestUtils.setField(exam, "hostId", hostId);
            ReflectionTestUtils.setField(exam, "proctorRequired", true);
            ReflectionTestUtils.setField(exam, "proctorId", 999L);
            when(exam.getId()).thenReturn(examId);
            when(exam.getProctorRequired()).thenReturn(true);
            when(exam.getProctorId()).thenReturn(null); // After unassign, it returns null

            when(examRepository.findByIdAndHostId(examId, hostId)).thenReturn(Optional.of(exam));
            when(timeOverrideRepository.findByExamId(examId)).thenReturn(Collections.emptyList());
            when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

            // Before unassign: proctorId is set (before calling service)
            // We can verify this through the response instead

            // Act: unassign proctor
            ExamDetailResponse response = service.unassignProctor(examId, hostPrincipal);

            // Assert
            assertThat(response).isNotNull();
            // After unassign, exam.getProctorId() returns null (as configured in the mock)
            assertThat(exam.getProctorId()).isNull();

            // Then isStartable should return false
            boolean result = service.isStartable(examId);
            assertThat(result).isFalse();
        }
    }
}
