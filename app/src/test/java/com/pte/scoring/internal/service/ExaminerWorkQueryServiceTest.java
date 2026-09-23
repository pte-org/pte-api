package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerWorkQueryServiceTest {

    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerAnswerScoreRepository examinerScoreRepository;
    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ScoringEligibilityQueryService eligibilityQueryService;

    private ExaminerWorkQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerWorkQueryService(assignmentRepository, examinerScoreRepository,
                answerRepository, eligibilityQueryService);
    }

    @Test
    void detailDoesNotReturnAnswerWhenTenantDoesNotOwnIt() {
        UUID answerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerId);
        answer.setTenantId(UUID.randomUUID());
        when(answerRepository.findByAnswerPublicId(answerId)).thenReturn(Optional.of(answer));

        assertThat(service.findWorkItem(tenantId, UUID.randomUUID(), answerId)).isEmpty();

        verify(assignmentRepository, never()).existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
