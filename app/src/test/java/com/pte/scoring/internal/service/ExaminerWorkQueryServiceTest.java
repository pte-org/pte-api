package com.pte.scoring.internal.service;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
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
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerEvaluationRepository examinerScoreRepository;
    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private ScoringEligibilityQueryService eligibilityQueryService;
    @Mock
    private AnswerContentReader contentReader;

    private ExaminerWorkQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExaminerWorkQueryService(assignmentRepository, examinerScoreRepository,
                answerRepository, eligibilityQueryService, contentReader);
    }

    @Test
    void detailDoesNotReturnAnswerWhenTenantDoesNotOwnIt() {
        UUID answerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation answer = com.pte.scoring.support.EvaluationFixtures.ai(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), answerId, "READ_ALOUD", com.pte.scoring.domain.enums.ScoringMethod.AI_SPEECH);
        when(answerRepository.findByAnswerPublicId(answerId)).thenReturn(Optional.of(answer));

        assertThat(service.findWorkItem(tenantId, UUID.randomUUID(), answerId)).isEmpty();

        verify(assignmentRepository, never()).existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
