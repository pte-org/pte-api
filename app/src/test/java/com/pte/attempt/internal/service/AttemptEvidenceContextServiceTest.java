package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.AttemptEvidenceContextView;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.shared.security.CurrentUser;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptEvidenceContextServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private AttemptEvidenceContextService service;
    private final UUID studentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttemptEvidenceContextService(attemptRepository);
    }

    private ExamAttempt attempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(11L);
        attempt.setPublicId(attemptId);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(studentId);
        attempt.setTenantId(tenantId);
        attempt.setLockdownMode("STRICT");
        return attempt;
    }

    @Test
    void ownAttempt_isLockedAndExposesOnlyTheFactsEvidenceNeeds() {
        ExamAttempt attempt = attempt();
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptId, studentId))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.findWithLockById(11L)).thenReturn(Optional.of(attempt));

        AttemptEvidenceContextView view = service.lockOwn(attemptId, new CurrentUser(studentId, tenantId,
                List.of("STUDENT")));

        assertThat(view.attemptPublicId()).isEqualTo(attemptId);
        assertThat(view.sessionPublicId()).isEqualTo(attempt.getSessionPublicId());
        assertThat(view.studentPublicId()).isEqualTo(studentId);
        assertThat(view.tenantId()).isEqualTo(tenantId);
        assertThat(view.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void anotherTenant_isNotFoundAndNeverLocks() {
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptId, studentId))
                .thenReturn(Optional.of(attempt()));

        assertThatThrownBy(() -> service.lockOwn(attemptId,
                new CurrentUser(studentId, UUID.randomUUID(), List.of("STUDENT"))))
                .isInstanceOf(AttemptNotFoundException.class);

        verify(attemptRepository, never()).findWithLockById(any());
    }

    @Test
    void someoneElsesAttempt_isNotFound() {
        when(attemptRepository.findByPublicIdAndStudentPublicId(attemptId, studentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockOwn(attemptId, new CurrentUser(studentId, tenantId, List.of("STUDENT"))))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void callerWithoutTenant_isNotFound() {
        assertThatThrownBy(() -> service.lockOwn(attemptId, new CurrentUser(studentId, null, List.of("STUDENT"))))
                .isInstanceOf(AttemptNotFoundException.class);
    }
}
