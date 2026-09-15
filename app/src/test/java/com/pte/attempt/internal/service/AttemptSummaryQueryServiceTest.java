package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptSummaryQueryServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private AttemptSummaryQueryService service;

    @BeforeEach
    void setUp() {
        service = new AttemptSummaryQueryService(attemptRepository);
    }

    @Test
    void findSubmitted_attemptExists_returnsMappedView() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.setStatus(AttemptStatus.SUBMITTED);

        when(attemptRepository.findByPublicIdAndStatus(attemptPublicId, AttemptStatus.SUBMITTED))
                .thenReturn(Optional.of(attempt));

        AttemptSummaryView result = service.findSubmitted(attemptPublicId);

        assertThat(result.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(result.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(result.studentPublicId()).isEqualTo(studentPublicId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void findSubmitted_attemptNotFound_throwsAttemptNotFoundException() {
        UUID attemptPublicId = UUID.randomUUID();

        when(attemptRepository.findByPublicIdAndStatus(attemptPublicId, AttemptStatus.SUBMITTED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSubmitted(attemptPublicId))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void findSubmitted_attemptNotSubmitted_throwsAttemptNotFoundException() {
        UUID attemptPublicId = UUID.randomUUID();

        // Repository call checks status, so not-SUBMITTED returns empty
        when(attemptRepository.findByPublicIdAndStatus(attemptPublicId, AttemptStatus.SUBMITTED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSubmitted(attemptPublicId))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void findSubmittedForSession_returnsEmptyList() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(attemptRepository.findBySessionPublicIdAndTenantIdAndStatus(sessionPublicId, tenantId, AttemptStatus.SUBMITTED))
                .thenReturn(List.of());

        List<AttemptSummaryView> results = service.findSubmittedForSession(sessionPublicId, tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void findSubmittedForSession_returnsMappedViews() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId1 = UUID.randomUUID();
        UUID studentId1 = UUID.randomUUID();
        UUID attemptId2 = UUID.randomUUID();
        UUID studentId2 = UUID.randomUUID();

        ExamAttempt attempt1 = new ExamAttempt();
        attempt1.setPublicId(attemptId1);
        attempt1.setSessionPublicId(sessionPublicId);
        attempt1.setStudentPublicId(studentId1);
        attempt1.setTenantId(tenantId);
        attempt1.setStatus(AttemptStatus.SUBMITTED);

        ExamAttempt attempt2 = new ExamAttempt();
        attempt2.setPublicId(attemptId2);
        attempt2.setSessionPublicId(sessionPublicId);
        attempt2.setStudentPublicId(studentId2);
        attempt2.setTenantId(tenantId);
        attempt2.setStatus(AttemptStatus.SUBMITTED);

        when(attemptRepository.findBySessionPublicIdAndTenantIdAndStatus(sessionPublicId, tenantId, AttemptStatus.SUBMITTED))
                .thenReturn(List.of(attempt1, attempt2));

        List<AttemptSummaryView> results = service.findSubmittedForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).attemptPublicId()).isEqualTo(attemptId1);
        assertThat(results.get(0).studentPublicId()).isEqualTo(studentId1);
        assertThat(results.get(1).attemptPublicId()).isEqualTo(attemptId2);
        assertThat(results.get(1).studentPublicId()).isEqualTo(studentId2);
    }

    @Test
    void findSubmittedForSession_mapsAllFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.setStatus(AttemptStatus.SUBMITTED);

        when(attemptRepository.findBySessionPublicIdAndTenantIdAndStatus(sessionPublicId, tenantId, AttemptStatus.SUBMITTED))
                .thenReturn(List.of(attempt));

        List<AttemptSummaryView> results = service.findSubmittedForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(1);
        AttemptSummaryView view = results.get(0);
        assertThat(view.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(view.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(view.studentPublicId()).isEqualTo(studentPublicId);
        assertThat(view.tenantId()).isEqualTo(tenantId);
    }
}
