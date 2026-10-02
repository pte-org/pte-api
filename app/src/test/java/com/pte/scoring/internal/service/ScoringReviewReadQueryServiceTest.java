package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringReviewReadQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ExaminerAnswerScoreRepository examinerScoreRepository;
    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private ScoringMethodResolver methodResolver;

    private ScoringReviewReadQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoringReviewReadQueryService(answerRepository, examinerScoreRepository,
                assignmentRepository, sessionStateRepository, methodResolver);
    }

    @Test
    void reportInputs_keepZeroObjectiveScorePublishable() {
        ScoringAnswer answer = answer("MC_READING_SINGLE");
        answer.markScored(0);
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "MC_READING_SINGLE"))
                .thenReturn(Optional.of(ScoringMethod.OBJECTIVE));
        when(methodResolver.resolveSection(templateId, "MC_READING_SINGLE")).thenReturn(Optional.of("READING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isZero();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_excludeUnscoredObjectiveWithoutBlockingPublication() {
        ScoringAnswer answer = answer("MC_READING_SINGLE");
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "MC_READING_SINGLE"))
                .thenReturn(Optional.of(ScoringMethod.OBJECTIVE));
        when(methodResolver.resolveSection(templateId, "MC_READING_SINGLE")).thenReturn(Optional.of("READING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isTrue();
        assertThat(input.blockingReason()).isNull();
    }

    @Test
    void reportInputs_requireSelectedSourceForAiEligibleAnswer() {
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(0, AiProviderCategory.REAL, "provider", "model", "v1");
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isNull();
        assertThat(input.publishable()).isFalse();
        assertThat(input.blockingReason()).isEqualTo("NO_SELECTED_SOURCE");
    }

    @Test
    void reportInputs_useOnlyTheSelectedRealAiScore() {
        ScoringAnswer answer = answer("READ_ALOUD");
        answer.markAiScored(88, AiProviderCategory.REAL, "provider", "model", "v1");
        answer.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now(), null);
        when(answerRepository.findByAttemptPublicIdAndTenantId(attemptId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));

        var input = service.findReportInputsForAttempt(tenantId, attemptId).getFirst();

        assertThat(input.selectedScore()).isEqualTo(88);
        assertThat(input.aiRawScore()).isEqualTo(88);
        assertThat(input.aiProviderCategory()).isEqualTo("REAL");
        assertThat(input.publishable()).isTrue();
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(attemptId);
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        return answer;
    }
}
