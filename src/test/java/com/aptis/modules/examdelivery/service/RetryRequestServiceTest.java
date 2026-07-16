package com.aptis.modules.examdelivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
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
import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;
import com.aptis.modules.examdelivery.dto.RetryRequestResponse;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examdelivery.repository.RetryRequestRepository;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.iam.repository.GraderOrgAssignmentRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryRequestService Tests")
class RetryRequestServiceTest {

    @Mock
    private RetryRequestRepository retryRequestRepository;

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private GraderOrgAssignmentRepository graderOrgAssignmentRepository;

    @Mock
    private ExamAttemptAccessGuard accessGuard;

    private RetryRequestService service;

    @BeforeEach
    void setUp() {
        service = new RetryRequestService(
                retryRequestRepository,
                examAttemptRepository,
                examRepository,
                graderOrgAssignmentRepository,
                accessGuard);
    }

    // ====================
    // requestRetry() Tests
    // ====================

    @Test
    @DisplayName("requestRetry: happy path creates PENDING request")
    void requestRetry_happyPath_createsPendingRequest() {
        Long attemptId = 100L;
        String studentId = "student-123";
        Long examId = 50L;
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        // Arrange: setup mocks
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn(studentId);
        when(attempt.getExamId()).thenReturn(examId);
        when(attempt.getIsSubmitted()).thenReturn(true);

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        Exam exam = mock(Exam.class);
        when(exam.getMaxRetryCount()).thenReturn(3);
        when(examRepository.findById(examId))
                .thenReturn(Optional.of(exam));

        when(examAttemptRepository.countByStudentIdAndExamId(studentId, examId))
                .thenReturn(1L); // 1 attempt so far (initial)

        when(retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(
                eq(studentId), eq(examId), anyList()))
                .thenReturn(false);

        // Mock the save to return the saved object
        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RetryRequestResponse response = service.requestRetry(attemptId, studentPrincipal);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(RetryRequestStatus.PENDING.name());
        assertThat(response.studentId()).isEqualTo(studentId);
        assertThat(response.examId()).isEqualTo(examId);

        verify(retryRequestRepository).save(any(RetryRequest.class));
    }

    @Test
    @DisplayName("requestRetry: attempt not submitted throws VALIDATION_ERROR")
    void requestRetry_attemptNotSubmitted_throws() {
        Long attemptId = 100L;
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getIsSubmitted()).thenReturn(false); // Not submitted

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        // Act & Assert
        assertThatThrownBy(() -> service.requestRetry(attemptId, studentPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestRetry: existing PENDING request blocks new request")
    void requestRetry_existingPendingBlocks() {
        Long attemptId = 100L;
        String studentId = "student-123";
        Long examId = 50L;
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn(studentId);
        when(attempt.getExamId()).thenReturn(examId);
        when(attempt.getIsSubmitted()).thenReturn(true);

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        // A PENDING request already exists
        when(retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(
                eq(studentId), eq(examId), anyList()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.requestRetry(attemptId, studentPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestRetry: existing APPROVED request blocks new request")
    void requestRetry_existingApprovedBlocks() {
        Long attemptId = 100L;
        String studentId = "student-123";
        Long examId = 50L;
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn(studentId);
        when(attempt.getExamId()).thenReturn(examId);
        when(attempt.getIsSubmitted()).thenReturn(true);

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        // An APPROVED request already exists
        when(retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(
                eq(studentId), eq(examId), anyList()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.requestRetry(attemptId, studentPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestRetry: budget exhausted at boundary (attemptsSoFar == maxRetryCount)")
    void requestRetry_budgetExhaustedAtBoundary() {
        Long attemptId = 100L;
        String studentId = "student-123";
        Long examId = 50L;
        Integer maxRetryCount = 2; // 1 initial attempt + 1 retry allowed
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn(studentId);
        when(attempt.getExamId()).thenReturn(examId);
        when(attempt.getIsSubmitted()).thenReturn(true);

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        when(retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(
                eq(studentId), eq(examId), anyList()))
                .thenReturn(false);

        Exam exam = mock(Exam.class);
        when(exam.getMaxRetryCount()).thenReturn(maxRetryCount);
        when(examRepository.findById(examId))
                .thenReturn(Optional.of(exam));

        // Exactly maxRetryCount attempts so far (budget is exhausted)
        when(examAttemptRepository.countByStudentIdAndExamId(studentId, examId))
                .thenReturn((long)maxRetryCount);

        // Act & Assert
        assertThatThrownBy(() -> service.requestRetry(attemptId, studentPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_CONFLICT);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestRetry: budget available just below boundary (attemptsSoFar < maxRetryCount)")
    void requestRetry_budgetAvailableBelowBoundary() {
        Long attemptId = 100L;
        String studentId = "student-123";
        Long examId = 50L;
        Integer maxRetryCount = 3; // 3 total attempts allowed
        long attemptsSoFar = 2; // Currently 2 attempts (can still request one more)
        JwtPrincipal studentPrincipal = new JwtPrincipal(1L, "STUDENT", "STUDENT", 1L);

        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getStudentId()).thenReturn(studentId);
        when(attempt.getExamId()).thenReturn(examId);
        when(attempt.getIsSubmitted()).thenReturn(true);

        when(accessGuard.loadOwnedAttempt(attemptId, studentPrincipal))
                .thenReturn(attempt);

        when(retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(
                eq(studentId), eq(examId), anyList()))
                .thenReturn(false);

        Exam exam = mock(Exam.class);
        when(exam.getMaxRetryCount()).thenReturn(maxRetryCount);
        when(examRepository.findById(examId))
                .thenReturn(Optional.of(exam));

        when(examAttemptRepository.countByStudentIdAndExamId(studentId, examId))
                .thenReturn(attemptsSoFar);

        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RetryRequestResponse response = service.requestRetry(attemptId, studentPrincipal);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(RetryRequestStatus.PENDING.name());

        verify(retryRequestRepository).save(any(RetryRequest.class));
    }

    // ====================
    // listPending() Tests
    // ====================

    @Test
    @DisplayName("listPending: HOST sees only own exams")
    void listPending_hostSeesOwnExamsOnly() {
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        // Prepare retry requests
        RetryRequest request1 = RetryRequest.create("stu-1", 1L, 100L);
        RetryRequest request2 = RetryRequest.create("stu-2", 2L, 101L);
        RetryRequest request3 = RetryRequest.create("stu-3", 3L, 102L);

        when(retryRequestRepository.findByStatus(RetryRequestStatus.PENDING))
                .thenReturn(Arrays.asList(request1, request2, request3));

        // Exam for request1: owned by hostPrincipal
        Exam exam1 = mock(Exam.class);
        when(exam1.getHostId()).thenReturn("111"); // matches hostPrincipal.userId()
        when(examRepository.findById(100L)).thenReturn(Optional.of(exam1));

        // Exam for request2: owned by different host
        Exam exam2 = mock(Exam.class);
        when(exam2.getHostId()).thenReturn("222"); // different host
        when(examRepository.findById(101L)).thenReturn(Optional.of(exam2));

        // Exam for request3: owned by hostPrincipal
        Exam exam3 = mock(Exam.class);
        when(exam3.getHostId()).thenReturn("111"); // matches hostPrincipal.userId()
        when(examRepository.findById(102L)).thenReturn(Optional.of(exam3));

        // Act
        List<RetryRequestResponse> results = service.listPending(hostPrincipal);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results).extracting(RetryRequestResponse::examId)
                .containsExactlyInAnyOrder(100L, 102L);
    }

    @Test
    @DisplayName("listPending: GRADER sees only org-assigned exams")
    void listPending_graderSeesOrgAssignedOnly() {
        JwtPrincipal graderPrincipal = new JwtPrincipal(222L, "GRADER", "GRADER_ROLE", 222L);

        RetryRequest request1 = RetryRequest.create("stu-1", 1L, 100L);
        RetryRequest request2 = RetryRequest.create("stu-2", 2L, 101L);
        RetryRequest request3 = RetryRequest.create("stu-3", 3L, 102L);

        when(retryRequestRepository.findByStatus(RetryRequestStatus.PENDING))
                .thenReturn(Arrays.asList(request1, request2, request3));

        // Exam 100: grader is assigned to orgId=10
        Exam exam1 = mock(Exam.class);
        when(exam1.getOrganizationId()).thenReturn(10L);
        when(examRepository.findById(100L)).thenReturn(Optional.of(exam1));
        when(graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(222L, 10L))
                .thenReturn(true);

        // Exam 101: grader is NOT assigned
        Exam exam2 = mock(Exam.class);
        when(exam2.getOrganizationId()).thenReturn(20L);
        when(examRepository.findById(101L)).thenReturn(Optional.of(exam2));
        when(graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(222L, 20L))
                .thenReturn(false);

        // Exam 102: grader is assigned to orgId=10
        Exam exam3 = mock(Exam.class);
        when(exam3.getOrganizationId()).thenReturn(10L);
        when(examRepository.findById(102L)).thenReturn(Optional.of(exam3));
        when(graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(222L, 10L))
                .thenReturn(true);

        // Act
        List<RetryRequestResponse> results = service.listPending(graderPrincipal);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results).extracting(RetryRequestResponse::examId)
                .containsExactlyInAnyOrder(100L, 102L);
    }

    @Test
    @DisplayName("listPending: unknown role sees nothing")
    void listPending_unknownRoleSeeNothing() {
        JwtPrincipal unknownPrincipal = new JwtPrincipal(333L, "UNKNOWN", "UNKNOWN_ROLE", 333L);

        RetryRequest request1 = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findByStatus(RetryRequestStatus.PENDING))
                .thenReturn(Arrays.asList(request1));

        // The canReview check will look up the exam but won't find a match,
        // so we don't need to mock the exam repository response here.
        // The test verifies that an unknown user type is denied access.

        // Act
        List<RetryRequestResponse> results = service.listPending(unknownPrincipal);

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("listPending: empty list when no pending requests")
    void listPending_emptyWhenNoPending() {
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        when(retryRequestRepository.findByStatus(RetryRequestStatus.PENDING))
                .thenReturn(List.of());

        // Act
        List<RetryRequestResponse> results = service.listPending(hostPrincipal);

        // Assert
        assertThat(results).isEmpty();
    }

    // ====================
    // approve() Tests
    // ====================

    @Test
    @DisplayName("approve: happy path approves request")
    void approve_happyPath() {
        Long requestId = 50L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        Exam exam = mock(Exam.class);
        when(exam.getHostId()).thenReturn("111");
        when(examRepository.findById(100L))
                .thenReturn(Optional.of(exam));

        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RetryRequestResponse response = service.approve(requestId, hostPrincipal);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(RetryRequestStatus.APPROVED.name());
        assertThat(response.reviewedBy()).isEqualTo(111L);

        verify(retryRequestRepository).save(any(RetryRequest.class));
    }

    @Test
    @DisplayName("approve: request not found throws RESOURCE_NOT_FOUND")
    void approve_notFound_throws() {
        Long requestId = 999L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.approve(requestId, hostPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: unauthorized reviewer throws RESOURCE_NOT_FOUND (not 403)")
    void approve_unauthorizedReviewer_throws404() {
        Long requestId = 50L;
        JwtPrincipal graderPrincipal = new JwtPrincipal(222L, "GRADER", "GRADER_ROLE", 222L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        // Exam is assigned to org 10, but grader is not assigned to that org
        Exam exam = mock(Exam.class);
        when(exam.getOrganizationId()).thenReturn(10L);
        when(examRepository.findById(100L))
                .thenReturn(Optional.of(exam));

        when(graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(222L, 10L))
                .thenReturn(false);

        // Act & Assert: should throw RESOURCE_NOT_FOUND, not forbidden
        assertThatThrownBy(() -> service.approve(requestId, graderPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: tenant mismatch (exam not found) throws RESOURCE_NOT_FOUND")
    void approve_examNotFound_throws404() {
        Long requestId = 50L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        // Exam not found
        when(examRepository.findById(100L))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.approve(requestId, hostPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(retryRequestRepository, never()).save(any());
    }

    // ====================
    // reject() Tests
    // ====================

    @Test
    @DisplayName("reject: happy path rejects request")
    void reject_happyPath() {
        Long requestId = 50L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        Exam exam = mock(Exam.class);
        when(exam.getHostId()).thenReturn("111");
        when(examRepository.findById(100L))
                .thenReturn(Optional.of(exam));

        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RetryRequestResponse response = service.reject(requestId, hostPrincipal);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(RetryRequestStatus.REJECTED.name());
        assertThat(response.reviewedBy()).isEqualTo(111L);

        verify(retryRequestRepository).save(any(RetryRequest.class));
    }

    @Test
    @DisplayName("reject: request not found throws RESOURCE_NOT_FOUND")
    void reject_notFound_throws() {
        Long requestId = 999L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.reject(requestId, hostPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reject: unauthorized reviewer throws RESOURCE_NOT_FOUND (not 403)")
    void reject_unauthorizedReviewer_throws404() {
        Long requestId = 50L;
        JwtPrincipal hostPrincipal = new JwtPrincipal(111L, "HOST", "HOST_ROLE", 111L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        // Exam is owned by different host
        Exam exam = mock(Exam.class);
        when(exam.getHostId()).thenReturn("999");
        when(examRepository.findById(100L))
                .thenReturn(Optional.of(exam));

        // Act & Assert
        assertThatThrownBy(() -> service.reject(requestId, hostPrincipal))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(retryRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reject: grader org assignment filters correctly")
    void reject_graderOrgAssignmentFiltering() {
        Long requestId = 50L;
        JwtPrincipal graderPrincipal = new JwtPrincipal(222L, "GRADER", "GRADER_ROLE", 222L);

        RetryRequest request = RetryRequest.create("stu-1", 1L, 100L);
        when(retryRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        // Exam has organizationId = 10
        Exam exam = mock(Exam.class);
        when(exam.getOrganizationId()).thenReturn(10L);
        when(examRepository.findById(100L))
                .thenReturn(Optional.of(exam));

        // Grader IS assigned to org 10
        when(graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(222L, 10L))
                .thenReturn(true);

        when(retryRequestRepository.save(any(RetryRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RetryRequestResponse response = service.reject(requestId, graderPrincipal);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(RetryRequestStatus.REJECTED.name());

        verify(retryRequestRepository).save(any(RetryRequest.class));
    }
}
