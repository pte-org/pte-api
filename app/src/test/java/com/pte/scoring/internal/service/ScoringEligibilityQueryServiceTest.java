package com.pte.scoring.internal.service;

import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringEligibilityQueryServiceTest {

    private final UUID templateId = UUID.randomUUID();
    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ScoreTemplateService scoreTemplateService;

    private ScoringEligibilityQueryService service;

    @BeforeEach
    void setUp() {
        service = new ScoringEligibilityQueryService(answerRepository, new ScoringMethodResolver(scoreTemplateService));
    }

    @Test
    void eligiblePoolUsesPinnedTemplateMethodAndGroupsAiAnswersByAttempt() {
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID aiAttempt = UUID.randomUUID();
        UUID objectiveAttempt = UUID.randomUUID();
        when(answerRepository.findBySessionPublicIdAndTenantId(session, tenant)).thenReturn(List.of(
                answer("CUSTOM_SPEECH", aiAttempt), answer("CUSTOM_TEXT", aiAttempt),
                answer("CUSTOM_OBJECTIVE", objectiveAttempt), answer("CUSTOM_UNSCORED", UUID.randomUUID())));
        when(scoreTemplateService.getByPublicId(templateId)).thenReturn(new ScoreTemplateResponse(
                templateId, "CUSTOM", 1, "Custom", "RETIRED", List.of(
                        item("CUSTOM_SPEECH", "AI_SPEECH"), item("CUSTOM_TEXT", "AI_TEXT"),
                        item("CUSTOM_OBJECTIVE", "OBJECTIVE"), item("CUSTOM_UNSCORED", "UNSCORED"))));

        List<AiEligibleAttemptView> result = service.findEligibleAttempts(session, tenant, null);

        assertThat(result).containsExactly(new AiEligibleAttemptView(aiAttempt, 2));
        verify(scoreTemplateService).getByPublicId(templateId);
    }

    @Test
    void emptyAttemptFilterDoesNotAccidentallyExpandToTheWholeSession() {
        assertThat(service.findEligibleAttempts(UUID.randomUUID(), UUID.randomUUID(), List.of())).isEmpty();
        verify(answerRepository, never()).findBySessionPublicIdAndTenantId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ScoringAnswer answer(String taskType, UUID attemptPublicId) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        answer.setAttemptPublicId(attemptPublicId);
        return answer;
    }

    private ScoreTemplateItemResponse item(String taskType, String method) {
        return new ScoreTemplateItemResponse(taskType, "SPEAKING", 0, 1, 1, 0, 0, method,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
