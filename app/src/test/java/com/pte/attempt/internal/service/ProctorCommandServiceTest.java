package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * New: backs the public {@code com.pte.attempt.AttemptService#forceSubmit}
 * that {@code proctoring} (Phase 09) will call directly, in-process. No
 * direct source test existed for {@code ProctorCommandService} — it was only
 * exercised indirectly through {@code ProctorCommandConsumer} (RabbitMQ),
 * which this port doesn't carry (no proctoring caller exists yet).
 */
@ExtendWith(MockitoExtension.class)
class ProctorCommandServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;

    private ProctorCommandService service;

    @BeforeEach
    void setUp() {
        service = new ProctorCommandService(attemptRepository);
    }

    @Test
    void forceSubmit_inProgressAttempt_submitsAndSaves() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.of(attempt));

        service.forceSubmit(attemptPublicId, tenantId);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        verify(attemptRepository).save(attempt);
    }

    @Test
    void forceSubmit_alreadySubmittedAttempt_isSilentNoOp() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setTenantId(tenantId);
        attempt.begin();
        attempt.submit();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.of(attempt));

        service.forceSubmit(attemptPublicId, tenantId);

        verify(attemptRepository, never()).save(any());
    }

    @Test
    void forceSubmit_unknownAttempt_isSilentNoOp() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(attemptRepository.findByPublicIdAndTenantId(attemptPublicId, tenantId)).thenReturn(Optional.empty());

        service.forceSubmit(attemptPublicId, tenantId);

        verify(attemptRepository, never()).save(any());
    }
}
