package com.pte.attempt;

import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.AnswerScoreSelectionService;
import com.pte.attempt.internal.service.AttemptEvidenceContextService;
import com.pte.attempt.internal.service.query.AttemptExaminerPromptQueryService;
import com.pte.attempt.internal.service.query.AttemptGradingQueryService;
import com.pte.attempt.internal.service.query.AttemptMonitoringQueryService;
import com.pte.attempt.internal.service.query.AttemptSummaryQueryService;
import com.pte.attempt.internal.service.ProctorCommandService;
import com.pte.attempt.internal.service.query.SubmittedAnswerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Identity asks the attempt module, through the shared port, whether a Student is mid-exam. */
@ExtendWith(MockitoExtension.class)
class AttemptServiceInProgressLookupTest {

    @Mock
    private ProctorCommandService proctorCommandService;
    @Mock
    private SubmittedAnswerQueryService submittedAnswerQueryService;
    @Mock
    private AttemptSummaryQueryService attemptSummaryQueryService;
    @Mock
    private AttemptExaminerPromptQueryService attemptExaminerPromptQueryService;
    @Mock
    private ExamAttemptRepository examAttemptRepository;
    @Mock
    private AttemptEvidenceContextService evidenceContextService;
    @Mock
    private AttemptGradingQueryService attemptGradingQueryService;
    @Mock
    private AttemptMonitoringQueryService attemptMonitoringQueryService;
    @Mock
    private AnswerScoreSelectionService answerScoreSelectionService;

    private AttemptService attemptService;

    @BeforeEach
    void setUp() {
        attemptService = new AttemptService(proctorCommandService, submittedAnswerQueryService,
                attemptSummaryQueryService, attemptExaminerPromptQueryService, examAttemptRepository,
                evidenceContextService, attemptGradingQueryService, attemptMonitoringQueryService,
                answerScoreSelectionService);
    }

    @Test
    void hasInProgressAttempt_trueWhenTheStudentHasALiveInProgressAttempt() {
        UUID studentPublicId = UUID.randomUUID();
        when(examAttemptRepository.existsByStudentPublicIdAndStatusAndDeletedFalse(
                studentPublicId, AttemptStatus.IN_PROGRESS)).thenReturn(true);

        assertThat(attemptService.hasInProgressAttempt(studentPublicId)).isTrue();
    }

    @Test
    void hasInProgressAttempt_falseWhenThereIsNone() {
        UUID studentPublicId = UUID.randomUUID();
        when(examAttemptRepository.existsByStudentPublicIdAndStatusAndDeletedFalse(
                studentPublicId, AttemptStatus.IN_PROGRESS)).thenReturn(false);

        assertThat(attemptService.hasInProgressAttempt(studentPublicId)).isFalse();
    }
}
